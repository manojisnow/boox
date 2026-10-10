package com.example.chatapp.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.engine.ChatContextService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.server.ResponseStatusException;

class AgentOrchestratorTest {
  private final AgentRunRepository repository = mock(AgentRunRepository.class);
  private final AgentExecutionService execution = mock(AgentExecutionService.class);
  private final ChatContextService context = mock(ChatContextService.class);
  private final Map<String, AgentRun> saved = new HashMap<>();
  private AgentRunService runs;
  private AgentOrchestrator orchestrator;

  @BeforeEach
  void setup() {
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              final AgentRun run = invocation.getArgument(0);
              saved.put(run.getId(), run);
              return run;
            });
    when(repository.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(saved.get(invocation.getArgument(0))));
    runs = new AgentRunService(repository);
    orchestrator = new AgentOrchestrator(runs, execution, context, new SyncTaskExecutor());
  }

  private AgentRunStartRequest request() {
    final AgentRunStartRequest request = new AgentRunStartRequest();
    request.setGoal("Analyse");
    request.setConversationId("conv");
    request.setServer("ollama");
    request.setModel("model");
    return request;
  }

  @Test
  void successfulAnswerPersistsAndNeverMutatesSystemPrompt() throws Exception {
    when(execution.execute(any())).thenReturn("Checked answer");
    final AgentRunView run = orchestrator.start(request());
    assertEquals(AgentRunStatus.COMPLETED, run.getStatus());
    verify(context).addMessage("conv", "assistant", "Checked answer");
    verify(context, never()).setSystemPrompt(any(), any());
    assertTrue(
        run.getSteps().stream().allMatch(step -> step.getStatus() == AgentStepStatus.COMPLETED));
  }

  @Test
  void modelFailureIsFailedAndDoesNotWriteSuccessReply() throws Exception {
    when(execution.execute(any())).thenThrow(new IllegalStateException("offline"));
    final AgentRunView run = orchestrator.start(request());
    assertEquals(AgentRunStatus.FAILED, run.getStatus());
    assertTrue(run.getError().contains("offline"));
    verify(context, never()).addMessage(eq("conv"), eq("assistant"), any());
  }

  @Test
  void cancellationWinsOverSuccessfulOutput() throws Exception {
    when(execution.execute(any()))
        .thenAnswer(
            invocation -> {
              final AgentRunView run = invocation.getArgument(0);
              runs.cancel(run.getId());
              return "Late answer";
            });
    final AgentRunView run = orchestrator.start(request());
    assertEquals(AgentRunStatus.CANCELLED, run.getStatus());
    verify(context, never()).addMessage(eq("conv"), eq("assistant"), any());
  }

  @Test
  void queuedCancellationStopsExecutionAndMissingRunIsIgnored() {
    final AgentRunView run = runs.create(request());
    runs.cancel(run.getId());
    orchestrator.schedule(run.getId());
    orchestrator.schedule("missing");
    assertEquals(AgentRunStatus.CANCELLED, runs.get(run.getId()).orElseThrow().getStatus());
    verifyNoInteractions(execution);
  }

  @Test
  void rejectsBusyQueueUnsupportedServerAndOversizeFiles() {
    final TaskExecutor rejected =
        task -> {
          throw new TaskRejectedException("busy");
        };
    orchestrator = new AgentOrchestrator(runs, execution, context, rejected);
    assertEquals(AgentRunStatus.FAILED, orchestrator.start(request()).getStatus());
    final AgentRunStartRequest bad = request();
    bad.setServer("unknown");
    assertThrows(ResponseStatusException.class, () -> orchestrator.start(bad));
    bad.setServer("ollama");
    final AgentAttachment file = new AgentAttachment();
    file.setName("large.txt");
    file.setContent("x".repeat(24_001));
    bad.setAttachments(List.of(file));
    assertThrows(ResponseStatusException.class, () -> orchestrator.start(bad));
  }

  @Test
  void interruptedRunsBecomeRetryableFailuresAndDuplicateTasksAreRejected() {
    final AgentRunView run = runs.create(request());
    when(repository.findByStatusIn(any())).thenReturn(List.of(saved.get(run.getId())));
    orchestrator.recoverInterrupted();
    assertEquals(AgentRunStatus.FAILED, runs.get(run.getId()).orElseThrow().getStatus());
    when(repository.existsByConversationIdAndStatusIn(eq("conv"), any())).thenReturn(true);
    assertThrows(ResponseStatusException.class, () -> orchestrator.start(request()));
  }

  @Test
  void cancellationExceptionAndFailureDuringCancellationRemainCancelled() throws Exception {
    doAnswer(
            invocation -> {
              final AgentRunView run = invocation.getArgument(0);
              runs.cancel(run.getId());
              throw new CancellationException();
            })
        .when(execution)
        .execute(any());
    assertEquals(AgentRunStatus.CANCELLED, orchestrator.start(request()).getStatus());
    doAnswer(
            invocation -> {
              final AgentRunView run = invocation.getArgument(0);
              runs.cancel(run.getId());
              throw new IllegalStateException("offline");
            })
        .when(execution)
        .execute(any());
    assertEquals(AgentRunStatus.CANCELLED, orchestrator.start(request()).getStatus());
  }

  @Test
  void followUpsRetainFilesAndScopeAcrossNewRequestsAndCanClearOrReplaceFiles() throws Exception {
    when(execution.execute(any())).thenReturn("answer");
    when(repository.findByConversationIdOrderByCreatedAtDesc("conv"))
        .thenAnswer(
            invocation ->
                saved.values().stream()
                    .sorted(java.util.Comparator.comparing(AgentRun::getCreatedAt).reversed())
                    .toList());
    final AgentRunStartRequest first = request();
    final AgentAttachment file = new AgentAttachment();
    file.setName("totals.csv");
    file.setContent("amount\n5\n7");
    first.setAttachments(List.of(file));
    final AgentRunView original = orchestrator.start(first);
    assertFalse(original.isAllowWebSearch());
    final AgentRunStartRequest followUp = request();
    followUp.setGoal("Explain that calculation");
    final AgentRunView next = orchestrator.start(followUp);
    assertEquals(List.of("totals.csv"), next.getAttachedFileNames());
    assertFalse(next.isAllowWebSearch());
    final AgentAttachment replacement = new AgentAttachment();
    replacement.setName("totals.csv");
    replacement.setContent("amount\n9");
    final AgentRunStartRequest changed = request();
    changed.setAttachments(List.of(replacement));
    final AgentRunView replaced = orchestrator.start(changed);
    assertTrue(runs.requestJson(replaced.getId()).contains("amount\\n9"));
    final AgentRunStartRequest clear = request();
    clear.setClearAttachments(true);
    clear.setAllowWebSearch(true);
    assertTrue(orchestrator.start(clear).getAttachedFileNames().isEmpty());
    assertTrue(orchestrator.start(request()).getAttachedFileNames().isEmpty());
    final AgentRunStartRequest other = request();
    other.setConversationId("other");
    assertTrue(orchestrator.start(other).getAttachedFileNames().isEmpty());
  }

  @Test
  void followUpsReuseOnlyCompletedSearchEvidence() throws Exception {
    when(repository.findByConversationIdOrderByCreatedAtDesc("conv"))
        .thenAnswer(invocation -> List.copyOf(saved.values()));
    final AgentRunView first = runs.create(request());
    runs.markRunning(first.getId());
    runs.progress(
        first.getId(),
        AgentStepType.TOOL,
        "Search result: facts",
        "Source https://example.com/facts",
        AgentStepStatus.COMPLETED);
    runs.complete(first.getId(), "answer");
    assertTrue(runs.priorEvidence("conv", "next").contains("https://example.com/facts"));
    assertEquals("", runs.priorEvidence("conv", first.getId()));
  }
}

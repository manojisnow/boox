package com.example.chatapp.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AgentRunServiceTest {

  @Mock private AgentRunRepository runs;
  private AgentRunService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service =
        new AgentRunService(
            runs, org.mockito.Mockito.mock(com.example.chatapp.engine.ChatContextService.class));
    when(runs.save(any(AgentRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void create_trimsInputAndAddsPendingPlanStep() {
    final AgentRunStartRequest request = request();
    request.setGoal("  Review the project  ");

    final AgentRunView run = service.create(request);

    assertEquals("Review the project", run.getGoal());
    assertEquals(AgentRunStatus.QUEUED, run.getStatus());
    assertEquals(1, run.getSteps().size());
    assertEquals(AgentStepType.PLAN, run.getSteps().getFirst().getType());
    assertEquals(AgentStepStatus.PENDING, run.getSteps().getFirst().getStatus());
    verify(runs).save(any(AgentRun.class));
  }

  @Test
  void get_returnsEmptyForUnknownRun() {
    when(runs.findById("missing")).thenReturn(Optional.empty());

    assertTrue(service.get("missing").isEmpty());
  }

  @Test
  void listForConversation_returnsMappedRuns() {
    final AgentRun run = run();
    when(runs.findByConversationIdOrderByCreatedAtDesc("conversation-1")).thenReturn(List.of(run));

    final List<AgentRunView> found = service.listForConversation("conversation-1");

    assertEquals(List.of(run.getId()), found.stream().map(AgentRunView::getId).toList());
  }

  @Test
  void markRunning_addsWorkingStep() {
    final AgentRun run = run();
    when(runs.findById(run.getId())).thenReturn(Optional.of(run));

    final AgentRunView updated = service.markRunning(run.getId()).orElseThrow();

    assertEquals(AgentRunStatus.RUNNING, updated.getStatus());
    assertEquals(AgentStepType.MODEL, updated.getSteps().getLast().getType());
    assertEquals(AgentStepStatus.RUNNING, updated.getSteps().getLast().getStatus());
  }

  @Test
  void complete_storesOutputAndTerminalStep() {
    final AgentRun run = run();
    when(runs.findById(run.getId())).thenReturn(Optional.of(run));

    final AgentRunView updated = service.complete(run.getId(), "Completed work").orElseThrow();

    assertEquals(AgentRunStatus.COMPLETED, updated.getStatus());
    assertEquals("Completed work", updated.getFinalOutput());
    assertEquals(AgentStepStatus.COMPLETED, updated.getSteps().getLast().getStatus());
  }

  @Test
  void fail_storesErrorAndTerminalStep() {
    final AgentRun run = run();
    when(runs.findById(run.getId())).thenReturn(Optional.of(run));

    final AgentRunView updated = service.fail(run.getId(), "No model available").orElseThrow();

    assertEquals(AgentRunStatus.FAILED, updated.getStatus());
    assertEquals("No model available", updated.getError());
    assertEquals(AgentStepStatus.FAILED, updated.getSteps().getLast().getStatus());
  }

  @Test
  void cancel_requestsCancellationThenFinishesAndLeavesCompletedRunAlone() {
    final AgentRun active = run();
    when(runs.findById(active.getId())).thenReturn(Optional.of(active));

    final AgentRunView cancellationRequested = service.cancel(active.getId()).orElseThrow();

    assertEquals(AgentRunStatus.CANCEL_REQUESTED, cancellationRequested.getStatus());
    assertTrue(service.isCancellationRequested(active.getId()));

    final AgentRunView cancelled = service.markCancelled(active.getId()).orElseThrow();

    assertEquals(AgentRunStatus.CANCELLED, cancelled.getStatus());
    assertEquals(AgentStepStatus.SKIPPED, cancelled.getSteps().getLast().getStatus());

    final AgentRun complete = run();
    complete.setStatus(AgentRunStatus.COMPLETED);
    when(runs.findById(complete.getId())).thenReturn(Optional.of(complete));

    final AgentRunView unchanged = service.cancel(complete.getId()).orElseThrow();

    assertEquals(AgentRunStatus.COMPLETED, unchanged.getStatus());
    assertEquals(0, unchanged.getSteps().size());
    assertFalse(unchanged.getStatus() == AgentRunStatus.CANCELLED);
  }

  private static AgentRunStartRequest request() {
    final AgentRunStartRequest request = new AgentRunStartRequest();
    request.setGoal("Review the project");
    request.setConversationId("conversation-1");
    request.setServer("ollama");
    request.setModel("phi4-mini");
    return request;
  }

  private static AgentRun run() {
    return new AgentRun("run-1", "conversation-1", "Review", "ollama", "phi4-mini");
  }
}

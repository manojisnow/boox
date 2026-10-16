package com.example.chatapp.agent;

import com.example.chatapp.engine.ChatContextService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Starts isolated, bounded agent tasks and records their actual outcome. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring-managed collaborators are retained for dependency injection.")
public class AgentOrchestrator {
  private static final Logger LOGGER = LoggerFactory.getLogger(AgentOrchestrator.class);
  private final AgentRunService runs;
  private final AgentExecutionService execution;
  private final ChatContextService context;
  private final TaskExecutor executor;

  @org.springframework.beans.factory.annotation.Autowired
  private com.example.chatapp.context.ExecutionCoordinator coordinator;

  @org.springframework.beans.factory.annotation.Autowired
  private com.example.chatapp.context.PersonalizationResolver personalization;

  @org.springframework.beans.factory.annotation.Autowired
  private com.example.chatapp.memory.CurrentUserResolver users;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private final java.util.Map<String, String> executionOwners =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.Map<String, String> executionTokens =
      new java.util.concurrent.ConcurrentHashMap<>();

  public AgentOrchestrator(
      final AgentRunService runs,
      final AgentExecutionService execution,
      final ChatContextService context,
      @Qualifier("agentTaskExecutor") final TaskExecutor executor) {
    this.runs = runs;
    this.execution = execution;
    this.context = context;
    this.executor = executor;
  }

  public AgentRunView start(final AgentRunStartRequest request) {
    String owner = users.currentUserId();
    AgentRunView accepted =
        coordinator.withOwner(
            owner,
            () -> {
              String token = coordinator.reserve(owner, request.getConversationId());
              boolean committed = false;
              try {
                AgentRunView result =
                    new org.springframework.transaction.support.TransactionTemplate(
                            transactionManager)
                        .execute(status -> accept(request));
                executionTokens.put(result.getId(), token);
                executionOwners.put(result.getId(), owner);
                committed = true;
                return result;
              } finally {
                if (!committed) coordinator.finish(owner, request.getConversationId(), token);
              }
            });
    schedule(accepted.getId());
    return runs.get(accepted.getId()).orElseThrow();
  }

  private AgentRunView accept(final AgentRunStartRequest request) {
    if (!"ollama".equals(request.getServer().trim())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Agent mode currently supports Ollama only.");
    }
    context.setMetadata(request.getConversationId(), request.getServer(), request.getModel());
    if (request.getChatRequest() != null && request.getChatRequest().getSystemPrompt() != null)
      context.setSystemPrompt(
          request.getConversationId(), request.getChatRequest().getSystemPrompt());
    runs.inheritEvidence(request);
    if (request.getAttachments().size() > 4)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Retained and new files exceed four files. Clear retained files first.");
    final int fileChars =
        request.getAttachments().stream().mapToInt(file -> file.getContent().length()).sum();
    if (fileChars > 24_000)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Attached files exceed 24,000 characters in total. Select smaller files or relevant excerpts.");
    final AgentRunView run;
    context.addMessage(
        request.getConversationId(),
        "user",
        request.getGoal()
            + request.getAttachments().stream()
                .map(file -> "\n\nAttached file: " + file.getName())
                .collect(java.util.stream.Collectors.joining()));
    run = runs.create(request);
    return run;
  }

  public void schedule(final String runId) {
    try {
      executor.execute(() -> execute(runId));
    } catch (java.util.concurrent.RejectedExecutionException exception) {
      try {
        runs.fail(runId, "The agent is busy. Please retry shortly.");
      } finally {
        String token = executionTokens.remove(runId);
        String owner = executionOwners.remove(runId);
        if (token != null && owner != null) coordinator.finishExecution(owner, token);
      }
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverInterrupted() {
    runs.recoverInterrupted();
  }

  private static java.util.Optional<AgentRunView> transition(
      final java.util.function.Supplier<java.util.Optional<AgentRunView>> operation) {
    for (int attempt = 0; ; attempt++) {
      try {
        return operation.get();
      } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
        if (attempt >= 2) throw conflict;
      }
    }
  }

  private void execute(final String runId) {
    String owner = executionOwners.get(runId);
    String token = executionTokens.get(runId);
    try {
      final AgentRunView run = transition(() -> runs.markRunning(runId)).orElse(null);
      if (run == null) return;
      if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
        transition(() -> runs.markCancelled(runId));
        return;
      }
      if (run.getStatus() != AgentRunStatus.RUNNING) return;
      AgentRunStartRequest request =
          new tools.jackson.databind.ObjectMapper()
              .readValue(runs.requestJson(runId), AgentRunStartRequest.class);
      com.example.chatapp.engine.GenerationConfig savedConfig =
          context.getGenerationConfig(run.getConversationId());
      Integer contextOverride =
          request.getChatRequest() != null && request.getChatRequest().isReplaceGenerationConfig()
              ? request.getChatRequest().getNumCtx()
              : savedConfig == null ? null : savedConfig.getNumCtx();
      int capacity = contextOverride == null ? 16384 : contextOverride;
      int budget = Math.max(0, capacity - Math.min(2048, capacity / 4) - 128);
      com.example.chatapp.context.PersonalizationContext snapshot =
          personalization.resolve(
              owner,
              run.getConversationId(),
              token,
              run.getGoal(),
              request.getSelectedMemoryIds(),
              budget);
      com.example.chatapp.context.PersonalizationContext.install(snapshot);
      com.example.chatapp.context.PersonalizationContext.guard(
          () -> coordinator.check(owner, run.getConversationId(), token));
      runs.setContextUsed(runId, snapshot.contextUsed());
      final String answer = execution.execute(run);
      coordinator.check(owner, run.getConversationId(), token);
      transition(() -> runs.complete(runId, answer));
    } catch (CancellationException exception) {
      recordFailure(runId, "Task cancelled");
    } catch (Exception exception) {
      LOGGER.error("Agent run {} failed: {}", runId, exception.getClass().getSimpleName());
      recordFailure(runId, "The agent could not produce an answer: " + exception.getMessage());
    } finally {
      com.example.chatapp.context.PersonalizationContext.clear();
      executionTokens.remove(runId);
      executionOwners.remove(runId);
      if (owner != null && token != null) coordinator.finishExecution(owner, token);
    }
  }

  private void recordFailure(final String runId, final String error) {
    try {
      transition(
          () ->
              runs.isCancellationRequested(runId)
                  ? runs.markCancelled(runId)
                  : runs.fail(runId, error));
    } catch (Exception failure) {
      LOGGER.error(
          "Could not persist terminal status for agent run {}: {}",
          runId,
          failure.getClass().getSimpleName());
    }
  }
}

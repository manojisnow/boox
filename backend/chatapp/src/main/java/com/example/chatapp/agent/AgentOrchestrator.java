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

  public synchronized AgentRunView start(final AgentRunStartRequest request) {
    if (!"ollama".equals(request.getServer().trim())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Agent mode currently supports Ollama only.");
    }
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
    final AgentRunView run = runs.create(request);
    context.setMetadata(run.getConversationId(), run.getServer(), run.getModel());
    context.addMessage(
        run.getConversationId(),
        "user",
        run.getGoal()
            + request.getAttachments().stream()
                .map(file -> "\n\nAttached file: " + file.getName())
                .collect(java.util.stream.Collectors.joining()));
    schedule(run.getId());
    return runs.get(run.getId()).orElseThrow();
  }

  public void schedule(final String runId) {
    try {
      executor.execute(() -> execute(runId));
    } catch (org.springframework.core.task.TaskRejectedException exception) {
      runs.fail(runId, "The agent is busy. Please retry shortly.");
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
    try {
      final AgentRunView run = transition(() -> runs.markRunning(runId)).orElse(null);
      if (run == null) return;
      if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
        transition(() -> runs.markCancelled(runId));
        return;
      }
      if (run.getStatus() != AgentRunStatus.RUNNING) return;
      final String answer = execution.execute(run);
      transition(() -> runs.complete(runId, answer));
    } catch (CancellationException exception) {
      recordFailure(runId, "Task cancelled");
    } catch (Exception exception) {
      LOGGER.error("Agent run {} failed", runId, exception);
      recordFailure(runId, "The agent could not produce an answer: " + exception.getMessage());
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
      LOGGER.error("Could not persist terminal status for agent run {}", runId, failure);
    }
  }
}

package com.example.chatapp.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the durable lifecycle and visible audit trail for agent tasks. */
@Service
public class AgentRunService {

  private final AgentRunRepository runs;

  public AgentRunService(final AgentRunRepository runs) {
    this.runs = runs;
  }

  /** Creates a queued run. The orchestrator is responsible for claiming and executing it. */
  @Transactional
  public AgentRunView create(final AgentRunStartRequest request) {
    final AgentRun run =
        new AgentRun(
            UUID.randomUUID().toString(),
            request.getConversationId().trim(),
            request.getGoal().trim(),
            request.getServer().trim(),
            request.getModel().trim());
    addStep(run, AgentStepType.PLAN, AgentStepStatus.PENDING, "Preparing task", null);
    return AgentRunView.from(runs.save(run));
  }

  @Transactional(readOnly = true)
  public Optional<AgentRunView> get(final String runId) {
    return runs.findById(runId).map(AgentRunView::from);
  }

  @Transactional(readOnly = true)
  public List<AgentRunView> listForConversation(final String conversationId) {
    return runs.findByConversationIdOrderByCreatedAtDesc(conversationId).stream()
        .map(AgentRunView::from)
        .toList();
  }

  /**
   * Requests cancellation; an in-flight model call is allowed to finish before cleanup completes.
   */
  @Transactional
  public Optional<AgentRunView> cancel(final String runId) {
    return runs.findById(runId)
        .map(
            run -> {
              if (!isTerminal(run.getStatus())
                  && run.getStatus() != AgentRunStatus.CANCEL_REQUESTED) {
                run.setStatus(AgentRunStatus.CANCEL_REQUESTED);
                addStep(run, AgentStepType.FINAL, AgentStepStatus.RUNNING, "Cancelling task", null);
              }
              return AgentRunView.from(runs.save(run));
            });
  }

  @Transactional
  public Optional<AgentRunView> markRunning(final String runId) {
    return updateStatus(runId, AgentRunStatus.RUNNING, "Working on task");
  }

  @Transactional(readOnly = true)
  public boolean isCancellationRequested(final String runId) {
    return runs.findById(runId)
        .map(run -> run.getStatus() == AgentRunStatus.CANCEL_REQUESTED)
        .orElse(false);
  }

  @Transactional
  public Optional<AgentRunView> markCancelled(final String runId) {
    return runs.findById(runId)
        .map(
            run -> {
              if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
                run.setStatus(AgentRunStatus.CANCELLED);
                addStep(run, AgentStepType.FINAL, AgentStepStatus.SKIPPED, "Task cancelled", null);
              }
              return AgentRunView.from(runs.save(run));
            });
  }

  @Transactional
  public Optional<AgentRunView> complete(final String runId, final String finalOutput) {
    return runs.findById(runId)
        .map(
            run -> {
              if (isTerminal(run.getStatus())) {
                return AgentRunView.from(run);
              }
              run.setFinalOutput(finalOutput);
              run.setStatus(AgentRunStatus.COMPLETED);
              addStep(run, AgentStepType.FINAL, AgentStepStatus.COMPLETED, "Task completed", null);
              return AgentRunView.from(runs.save(run));
            });
  }

  @Transactional
  public Optional<AgentRunView> fail(final String runId, final String error) {
    return runs.findById(runId)
        .map(
            run -> {
              if (isTerminal(run.getStatus())) {
                return AgentRunView.from(run);
              }
              run.setError(error);
              run.setStatus(AgentRunStatus.FAILED);
              addStep(run, AgentStepType.FINAL, AgentStepStatus.FAILED, "Task failed", error);
              return AgentRunView.from(runs.save(run));
            });
  }

  private Optional<AgentRunView> updateStatus(
      final String runId, final AgentRunStatus status, final String summary) {
    return runs.findById(runId)
        .map(
            run -> {
              if (!isTerminal(run.getStatus())) {
                run.getSteps().stream()
                    .filter(step -> step.getType() == AgentStepType.PLAN)
                    .filter(step -> step.getStatus() == AgentStepStatus.PENDING)
                    .findFirst()
                    .ifPresent(step -> step.setStatus(AgentStepStatus.COMPLETED));
                run.setStatus(status);
                addStep(run, AgentStepType.MODEL, AgentStepStatus.RUNNING, summary, null);
              }
              return AgentRunView.from(runs.save(run));
            });
  }

  private static void addStep(
      final AgentRun run,
      final AgentStepType type,
      final AgentStepStatus status,
      final String summary,
      final String detail) {
    final AgentStep step = new AgentStep(run, run.getSteps().size() + 1, type, summary, detail);
    step.setStatus(status);
    run.addStep(step);
  }

  private static boolean isTerminal(final AgentRunStatus status) {
    return status == AgentRunStatus.COMPLETED
        || status == AgentRunStatus.FAILED
        || status == AgentRunStatus.CANCELLED;
  }
}

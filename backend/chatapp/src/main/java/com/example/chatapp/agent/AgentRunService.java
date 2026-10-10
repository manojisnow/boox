package com.example.chatapp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns the durable lifecycle and visible audit trail for agent tasks. */
@Service
public class AgentRunService {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final List<AgentRunStatus> ACTIVE =
      List.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING, AgentRunStatus.CANCEL_REQUESTED);
  private final AgentRunRepository runs;

  public AgentRunService(final AgentRunRepository runs) {
    this.runs = runs;
  }

  /** Creates a queued run. The orchestrator is responsible for claiming and executing it. */
  @Transactional
  public AgentRunView create(final AgentRunStartRequest request) {
    if (runs.existsByConversationIdAndStatusIn(request.getConversationId().trim(), ACTIVE)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This conversation already has an active agent task.");
    }
    final AgentRun run =
        new AgentRun(
            UUID.randomUUID().toString(),
            request.getConversationId().trim(),
            request.getGoal().trim(),
            request.getServer().trim(),
            request.getModel().trim());
    run.setRequestJson(MAPPER.valueToTree(request).toString());
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
    return updateStatus(runId, AgentRunStatus.RUNNING, "Working on the request");
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
                finishSteps(run, AgentStepStatus.SKIPPED);
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
              if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
                return markCancelled(runId).orElseThrow();
              }
              finishSteps(run, AgentStepStatus.COMPLETED);
              run.setFinalOutput(finalOutput);
              run.setStatus(AgentRunStatus.COMPLETED);
              addStep(run, AgentStepType.FINAL, AgentStepStatus.COMPLETED, "Response ready", null);
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
              finishSteps(run, AgentStepStatus.FAILED);
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
              if (run.getStatus() == AgentRunStatus.QUEUED) {
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

  @Transactional(readOnly = true)
  public String requestJson(final String runId) {
    return runs.findById(runId).map(AgentRun::getRequestJson).orElse(null);
  }

  @Transactional
  public void progress(
      final String runId,
      final AgentStepType type,
      final String summary,
      final String detail,
      final AgentStepStatus status) {
    runs.findById(runId)
        .ifPresent(
            run -> {
              if (run.getStatus() == AgentRunStatus.RUNNING) {
                finishSteps(run, AgentStepStatus.COMPLETED);
                addStep(run, type, status, summary, detail);
                runs.save(run);
              }
            });
  }

  @Transactional
  public void recoverInterrupted() {
    for (final AgentRun run : runs.findByStatusIn(ACTIVE)) {
      finishSteps(run, AgentStepStatus.FAILED);
      run.setStatus(AgentRunStatus.FAILED);
      run.setError("The server restarted before this task finished. Please retry the task.");
      addStep(
          run, AgentStepType.FINAL, AgentStepStatus.FAILED, "Interrupted by server restart", null);
      runs.save(run);
    }
  }

  private static void finishSteps(final AgentRun run, final AgentStepStatus status) {
    run.getSteps().stream()
        .filter(
            step ->
                step.getStatus() == AgentStepStatus.RUNNING
                    || step.getStatus() == AgentStepStatus.PENDING)
        .forEach(step -> step.setStatus(status));
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

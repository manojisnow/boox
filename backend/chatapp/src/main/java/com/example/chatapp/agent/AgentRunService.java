package com.example.chatapp.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Owns the durable lifecycle and visible audit trail for agent tasks. */
@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring-managed collaborators are retained for dependency injection.")
public class AgentRunService {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final List<AgentRunStatus> ACTIVE =
      List.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING, AgentRunStatus.CANCEL_REQUESTED);
  private final AgentRunRepository runs;
  private final com.example.chatapp.engine.ChatContextService context;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.example.chatapp.memory.CurrentUserResolver users;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.example.chatapp.persistence.ConversationRepository conversations;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.example.chatapp.context.ExecutionCoordinator coordinator;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private org.springframework.transaction.PlatformTransactionManager transactionManager;

  public AgentRunService(
      final AgentRunRepository runs, final com.example.chatapp.engine.ChatContextService context) {
    this.runs = runs;
    this.context = context;
  }

  /** Carry evidence and access scope forward, while allowing explicit replacement or clearing. */
  @Transactional(readOnly = true)
  public void inheritEvidence(final AgentRunStartRequest request) {
    assertConversation(request.getConversationId().trim(), true);
    if (request.getAllowWebSearch() == null && !request.getAttachments().isEmpty())
      request.setAllowWebSearch(false);
    final java.util.Map<String, AgentAttachment> files = new java.util.LinkedHashMap<>();
    final List<AgentRun> previous =
        runs.findByConversationIdOrderByCreatedAtDesc(request.getConversationId().trim());
    for (final AgentRun run : previous) {
      final String storedJson = run.getRequestJson();
      if (storedJson == null) continue;
      final AgentRunStartRequest stored;
      try {
        stored = MAPPER.readValue(storedJson, AgentRunStartRequest.class);
      } catch (tools.jackson.core.JacksonException exception) {
        throw new IllegalStateException("Stored task evidence could not be read.", exception);
      }
      if (request.getAllowWebSearch() == null) request.setAllowWebSearch(stored.permitsWebSearch());
      if (!request.isClearAttachments()) {
        for (final AgentAttachment file : stored.getAttachments())
          files.putIfAbsent(file.getName(), file);
      }
      if (!stored.getAttachments().isEmpty() || stored.isClearAttachments()) break;
    }
    for (final AgentAttachment file : request.getAttachments()) files.put(file.getName(), file);
    request.setAttachments(List.copyOf(files.values()));
    if (request.getAllowWebSearch() == null) request.setAllowWebSearch(request.permitsWebSearch());
  }

  /** Bounded source excerpts from completed tasks, so follow-ups can reuse actual evidence. */
  @Transactional(readOnly = true)
  public String priorEvidence(final String conversationId, final String currentId) {
    assertConversation(conversationId, false);
    final StringBuilder evidence = new StringBuilder();
    for (final AgentRun run : runs.findByConversationIdOrderByCreatedAtDesc(conversationId)) {
      if (java.util.Objects.equals(run.getId(), currentId)
          || run.getStatus() != AgentRunStatus.COMPLETED) continue;
      for (final AgentStep step : run.getSteps().reversed()) {
        if (step.getType() != AgentStepType.TOOL
            || step.getStatus() != AgentStepStatus.COMPLETED
            || step.getDetail() == null
            || !(step.getSummary().startsWith("Search result:")
                || step.getSummary().startsWith("Page result:"))) continue;
        final String excerpt = "\nRetrieved source excerpt:\n" + step.getDetail();
        if (evidence.length() + excerpt.length() > 8_000) return evidence.toString();
        evidence.append(excerpt);
      }
    }
    return evidence.toString();
  }

  /** Creates a queued run. The orchestrator is responsible for claiming and executing it. */
  @Transactional
  public AgentRunView create(final AgentRunStartRequest request) {
    assertConversation(request.getConversationId().trim(), false);
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
    return ownedRun(runId).map(AgentRunView::from);
  }

  @Transactional(readOnly = true)
  public List<AgentRunView> listForConversation(final String conversationId) {
    assertConversation(conversationId, true);
    return runs.findByConversationIdOrderByCreatedAtDesc(conversationId).stream()
        .map(AgentRunView::from)
        .toList();
  }

  /**
   * Requests cancellation; an in-flight model call is allowed to finish before cleanup completes.
   */
  public Optional<AgentRunView> cancel(final String runId) {
    java.util.function.Supplier<Optional<AgentRunView>> mutation =
        () ->
            ownedRun(runId)
                .map(
                    run -> {
                      if (!isTerminal(run.getStatus())
                          && run.getStatus() != AgentRunStatus.CANCEL_REQUESTED) {
                        run.setStatus(AgentRunStatus.CANCEL_REQUESTED);
                        addStep(
                            run,
                            AgentStepType.FINAL,
                            AgentStepStatus.RUNNING,
                            "Cancelling task",
                            null);
                      }
                      return AgentRunView.from(runs.save(run));
                    });
    java.util.function.Supplier<Optional<AgentRunView>> transactional =
        transactionManager == null
            ? mutation
            : () ->
                new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                    .execute(status -> mutation.get());
    if (users == null || coordinator == null) return transactional.get();
    String owner = users.currentUserId();
    return coordinator.withOwner(
        owner,
        () -> {
          Optional<AgentRunView> committed = transactional.get();
          committed
              .filter(run -> run.getStatus() == AgentRunStatus.CANCEL_REQUESTED)
              .ifPresent(run -> coordinator.cancellationRequested(owner, run.getConversationId()));
          return committed;
        });
  }

  @Transactional
  public Optional<AgentRunView> markRunning(final String runId) {
    return updateStatus(runId, AgentRunStatus.RUNNING, "Working on the request");
  }

  @Transactional(readOnly = true)
  public boolean isCancellationRequested(final String runId) {
    return ownedRun(runId)
        .map(run -> run.getStatus() == AgentRunStatus.CANCEL_REQUESTED)
        .orElse(false);
  }

  @Transactional
  public Optional<AgentRunView> markCancelled(final String runId) {
    return ownedRun(runId)
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
    return ownedRun(runId)
        .map(
            run -> {
              if (isTerminal(run.getStatus())) {
                return AgentRunView.from(run);
              }
              if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
                return markCancelled(runId).orElseThrow();
              }
              // Joins this transaction: history and terminal state commit or roll back together.
              context.addMessage(run.getConversationId(), "assistant", finalOutput);
              finishSteps(run, AgentStepStatus.COMPLETED);
              run.setFinalOutput(finalOutput);
              run.setStatus(AgentRunStatus.COMPLETED);
              addStep(run, AgentStepType.FINAL, AgentStepStatus.COMPLETED, "Response ready", null);
              return AgentRunView.from(runs.save(run));
            });
  }

  @Transactional
  public Optional<AgentRunView> fail(final String runId, final String error) {
    return ownedRun(runId)
        .map(
            run -> {
              if (isTerminal(run.getStatus())) {
                return AgentRunView.from(run);
              }
              if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
                return markCancelled(runId).orElseThrow();
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
    return ownedRun(runId)
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
    return ownedRun(runId).map(AgentRun::getRequestJson).orElse(null);
  }

  @Transactional
  public void progress(
      final String runId,
      final AgentStepType type,
      final String summary,
      final String detail,
      final AgentStepStatus status) {
    ownedRun(runId)
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
      if (users != null && ownedRun(run.getId()).isEmpty()) continue;
      finishSteps(run, AgentStepStatus.FAILED);
      run.setStatus(AgentRunStatus.FAILED);
      run.setError("The server restarted before this task finished. Please retry the task.");
      addStep(
          run, AgentStepType.FINAL, AgentStepStatus.FAILED, "Interrupted by server restart", null);
      runs.save(run);
    }
  }

  @Transactional
  public void setContextUsed(final String runId, final java.util.Map<String, Object> metadata) {
    ownedRun(runId)
        .ifPresent(
            run -> {
              run.setContextUsedJson(MAPPER.writeValueAsString(metadata));
              runs.save(run);
            });
  }

  private Optional<AgentRun> ownedRun(final String runId) {
    return runs.findById(runId)
        .filter(
            run -> {
              if (users == null) return true;
              return conversations
                  .findById(run.getConversationId())
                  .map(conversation -> users.currentUserId().equals(conversation.getOwnerId()))
                  .orElse(false);
            });
  }

  private void assertConversation(final String conversationId, final boolean allowMissing) {
    if (users == null) return;
    var conversation = conversations.findById(conversationId);
    if (conversation.isEmpty() && allowMissing) return;
    if (conversation.isEmpty()
        || !users.currentUserId().equals(conversation.orElseThrow().getOwnerId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found.");
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

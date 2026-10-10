package com.example.chatapp.agent;

import java.time.Instant;
import java.util.List;

/** Immutable API representation of an agent run and its visible activity. */
public final class AgentRunView {
  private String requestJson;
  private final String id;
  private final String conversationId;
  private final String goal;
  private final String server;
  private final String model;
  private final AgentRunStatus status;
  private final String finalOutput;
  private final String error;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final List<AgentStepView> steps;

  public AgentRunView(
      final String id,
      final String conversationId,
      final String goal,
      final String server,
      final String model,
      final AgentRunStatus status,
      final String finalOutput,
      final String error,
      final Instant createdAt,
      final Instant updatedAt,
      final List<AgentStepView> steps) {
    this.id = id;
    this.conversationId = conversationId;
    this.goal = goal;
    this.server = server;
    this.model = model;
    this.status = status;
    this.finalOutput = finalOutput;
    this.error = error;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.steps = List.copyOf(steps);
  }

  static AgentRunView from(final AgentRun run) {
    final AgentRunView view =
        new AgentRunView(
            run.getId(),
            run.getConversationId(),
            run.getGoal(),
            run.getServer(),
            run.getModel(),
            run.getStatus(),
            run.getFinalOutput(),
            run.getError(),
            run.getCreatedAt(),
            run.getUpdatedAt(),
            run.getSteps().stream().map(AgentStepView::from).toList());
    view.requestJson = run.getRequestJson();
    return view;
  }

  private AgentRunStartRequest request() {
    try {
      return requestJson == null
          ? new AgentRunStartRequest()
          : new tools.jackson.databind.ObjectMapper()
              .readValue(requestJson, AgentRunStartRequest.class);
    } catch (tools.jackson.core.JacksonException exception) {
      throw new IllegalStateException("Stored task scope could not be read.", exception);
    }
  }

  public boolean isAllowWebSearch() {
    return request().permitsWebSearch();
  }

  public List<String> getAttachedFileNames() {
    return request().getAttachments().stream().map(AgentAttachment::getName).toList();
  }

  public String getId() {
    return id;
  }

  public String getConversationId() {
    return conversationId;
  }

  public String getGoal() {
    return goal;
  }

  public String getServer() {
    return server;
  }

  public String getModel() {
    return model;
  }

  public AgentRunStatus getStatus() {
    return status;
  }

  public String getFinalOutput() {
    return finalOutput;
  }

  public String getError() {
    return error;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<AgentStepView> getSteps() {
    return steps;
  }
}

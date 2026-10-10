package com.example.chatapp.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Input for creating an agent task; execution is added by the orchestrator layer. */
public class AgentRunStartRequest {
  private static final int MAX_GOAL_LENGTH = 20_000;

  @NotBlank
  @Size(max = MAX_GOAL_LENGTH)
  private String goal;

  @NotBlank private String conversationId;
  @NotBlank private String server;
  @NotBlank private String model;

  public AgentRunStartRequest() {}

  public String getGoal() {
    return goal;
  }

  public void setGoal(final String goal) {
    this.goal = goal;
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(final String conversationId) {
    this.conversationId = conversationId;
  }

  public String getServer() {
    return server;
  }

  public void setServer(final String server) {
    this.server = server;
  }

  public String getModel() {
    return model;
  }

  public void setModel(final String model) {
    this.model = model;
  }
}

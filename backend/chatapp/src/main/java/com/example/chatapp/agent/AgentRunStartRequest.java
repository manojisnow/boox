package com.example.chatapp.agent;

import com.example.chatapp.controller.SendMessageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Input for creating an agent task; execution is added by the orchestrator layer. */
public class AgentRunStartRequest {
  private static final int MAX_GOAL_LENGTH = 20_000;

  @NotBlank
  @Size(max = MAX_GOAL_LENGTH)
  private String goal;

  @NotBlank private String conversationId;
  @NotBlank private String server;
  @NotBlank private String model;

  // Reuses chat validation for settings, system prompt, and optional image attachments.
  @Valid private SendMessageRequest chatRequest;

  @Size(max = 5)
  private List<String> selectedMemoryIds;

  public List<String> getSelectedMemoryIds() {
    if (selectedMemoryIds != null) return List.copyOf(selectedMemoryIds);
    return chatRequest == null ? List.of() : chatRequest.getSelectedMemoryIds();
  }

  public void setSelectedMemoryIds(List<String> ids) {
    selectedMemoryIds = ids == null ? null : List.copyOf(ids);
  }

  private boolean clearAttachments;

  public boolean isClearAttachments() {
    return clearAttachments;
  }

  public void setClearAttachments(final boolean clearAttachments) {
    this.clearAttachments = clearAttachments;
  }

  private Boolean allowWebSearch;

  public Boolean getAllowWebSearch() {
    return allowWebSearch;
  }

  public void setAllowWebSearch(final Boolean allowWebSearch) {
    this.allowWebSearch = allowWebSearch;
  }

  public boolean permitsWebSearch() {
    return allowWebSearch == null ? getAttachments().isEmpty() : allowWebSearch;
  }

  @Size(max = 4)
  private List<@Valid AgentAttachment> attachments;

  public List<AgentAttachment> getAttachments() {
    return attachments == null ? List.of() : List.copyOf(attachments);
  }

  public void setAttachments(final List<AgentAttachment> attachments) {
    this.attachments = attachments == null ? null : List.copyOf(attachments);
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Request-scoped validated settings are serialized before task execution.")
  public SendMessageRequest getChatRequest() {
    return chatRequest;
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Request-scoped validated settings are serialized before task execution.")
  public void setChatRequest(final SendMessageRequest chatRequest) {
    this.chatRequest = chatRequest;
  }

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

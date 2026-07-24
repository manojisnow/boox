package com.example.chatapp.controller.dto;

import java.time.Instant;

/** Sidebar list item for a persisted conversation. */
public final class ConversationSummary {

  private final String id;
  private final String title;
  private final String server;
  private final String model;
  private final Instant updatedAt;

  public ConversationSummary(
      final String id,
      final String title,
      final String server,
      final String model,
      final Instant updatedAt) {
    this.id = id;
    this.title = title;
    this.server = server;
    this.model = model;
    this.updatedAt = updatedAt;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getServer() {
    return server;
  }

  public String getModel() {
    return model;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

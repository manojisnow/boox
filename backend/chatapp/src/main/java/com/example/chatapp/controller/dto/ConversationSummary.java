package com.example.chatapp.controller.dto;

import java.time.Instant;
import java.util.List;

/** Sidebar list item for a persisted conversation. */
public final class ConversationSummary {

  private final String id;
  private final String title;
  private final String server;
  private final String model;
  private final Instant updatedAt;
  private final Double temperature;
  private final Integer numCtx;
  private final List<String> stopSequences;

  public ConversationSummary(
      final String id,
      final String title,
      final String server,
      final String model,
      final Instant updatedAt,
      final Double temperature,
      final Integer numCtx,
      final List<String> stopSequences) {
    this.id = id;
    this.title = title;
    this.server = server;
    this.model = model;
    this.updatedAt = updatedAt;
    this.temperature = temperature;
    this.numCtx = numCtx;
    this.stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
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

  public Double getTemperature() {
    return temperature;
  }

  public Integer getNumCtx() {
    return numCtx;
  }

  public List<String> getStopSequences() {
    return stopSequences;
  }
}

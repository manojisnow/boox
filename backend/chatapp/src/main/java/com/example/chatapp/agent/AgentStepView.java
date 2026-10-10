package com.example.chatapp.agent;

import java.time.Instant;

/** Immutable API representation of a durable agent step. */
public final class AgentStepView {
  private final int sequence;
  private final AgentStepType type;
  private final AgentStepStatus status;
  private final String summary;
  private final String detail;
  private final Instant createdAt;
  private final Instant completedAt;

  public AgentStepView(
      final int sequence,
      final AgentStepType type,
      final AgentStepStatus status,
      final String summary,
      final String detail,
      final Instant createdAt,
      final Instant completedAt) {
    this.sequence = sequence;
    this.type = type;
    this.status = status;
    this.summary = summary;
    this.detail = detail;
    this.createdAt = createdAt;
    this.completedAt = completedAt;
  }

  static AgentStepView from(final AgentStep step) {
    return new AgentStepView(
        step.getSequence(),
        step.getType(),
        step.getStatus(),
        step.getSummary(),
        step.getDetail(),
        step.getCreatedAt(),
        step.getCompletedAt());
  }

  public int getSequence() {
    return sequence;
  }

  public AgentStepType getType() {
    return type;
  }

  public AgentStepStatus getStatus() {
    return status;
  }

  public String getSummary() {
    return summary;
  }

  public String getDetail() {
    return detail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}

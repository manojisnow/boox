package com.example.chatapp.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** One visible, ordered unit of work performed during an {@link AgentRun}. */
@Entity
@Table(name = "agent_step")
public class AgentStep {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "integer")
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "agent_run_id", nullable = false)
  private AgentRun agentRun;

  @Column(nullable = false)
  private int sequence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentStepType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentStepStatus status = AgentStepStatus.PENDING;

  @Column(nullable = false, columnDefinition = "text")
  private String summary;

  @Column(columnDefinition = "text")
  private String detail;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  private Instant completedAt;

  public AgentStep() {}

  public AgentStep(
      final AgentRun agentRun,
      final int sequence,
      final AgentStepType type,
      final String summary,
      final String detail) {
    this.agentRun = agentRun;
    this.sequence = sequence;
    this.type = type;
    this.summary = summary;
    this.detail = detail;
  }

  public Long getId() {
    return id;
  }

  public AgentRun getAgentRun() {
    return agentRun;
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

  public void setStatus(final AgentStepStatus status) {
    this.status = status;
    if (status == AgentStepStatus.COMPLETED
        || status == AgentStepStatus.FAILED
        || status == AgentStepStatus.SKIPPED) {
      completedAt = Instant.now();
    }
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

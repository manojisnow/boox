package com.example.chatapp.agent;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Persistable;

/** A persisted, bounded attempt by an agent to complete one user goal. */
@Entity
@Table(name = "agent_run")
public class AgentRun implements Persistable<String> {

  @Id private String id;

  @Transient private boolean newEntity = true;

  @Column(nullable = false)
  private String conversationId;

  @Column(nullable = false, columnDefinition = "text")
  private String goal;

  @Column(nullable = false)
  private String server;

  @Column(nullable = false)
  private String model;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentRunStatus status = AgentRunStatus.QUEUED;

  @Column(columnDefinition = "text")
  private String finalOutput;

  @Column(columnDefinition = "text")
  private String error;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  @OneToMany(mappedBy = "agentRun", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sequence ASC")
  private List<AgentStep> steps = new ArrayList<>();

  public AgentRun() {}

  public AgentRun(
      final String id,
      final String conversationId,
      final String goal,
      final String server,
      final String model) {
    this.id = id;
    this.conversationId = conversationId;
    this.goal = goal;
    this.server = server;
    this.model = model;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    newEntity = false;
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

  public void setStatus(final AgentRunStatus status) {
    this.status = status;
    updatedAt = Instant.now();
  }

  public String getFinalOutput() {
    return finalOutput;
  }

  public void setFinalOutput(final String finalOutput) {
    this.finalOutput = finalOutput;
    updatedAt = Instant.now();
  }

  public String getError() {
    return error;
  }

  public void setError(final String error) {
    this.error = error;
    updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<AgentStep> getSteps() {
    return steps;
  }

  public void addStep(final AgentStep step) {
    steps.add(step);
    updatedAt = Instant.now();
  }
}

package com.example.chatapp.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A persisted chat conversation. Its {@code id} is the client-supplied session id.
 *
 * <p>Implements {@link Persistable} because the id is assigned (not generated): without it Spring
 * Data would treat every {@code save} as a merge, which diverges the managed instance and breaks
 * child references when the flush is deferred (e.g. inside one transaction).
 */
@Entity
@Table(name = "conversation")
public class Conversation implements Persistable<String> {

  /** Default title until the first user message supplies one. */
  public static final String DEFAULT_TITLE = "New chat";

  @Id private String id;

  @Transient private boolean isNew = true;

  @Column(nullable = false)
  private String title = DEFAULT_TITLE;

  private String server;

  private String model;

  @Column(columnDefinition = "text")
  private String systemPrompt;

  /** Running summary of older turns that have fallen out of the context window. */
  @Column(columnDefinition = "text")
  private String summary;

  /**
   * How many of the oldest messages are already folded into {@link #summary}. The explicit default
   * lets SQLite's {@code ALTER TABLE ADD COLUMN} add this NOT NULL column to tables that already
   * have rows (a bare {@code NOT NULL} add is rejected by SQLite).
   */
  @Column(columnDefinition = "integer not null default 0")
  private int summarizedCount;

  /** Per-conversation override of the server-default generation temperature; null = use default. */
  private Double temperature;

  /** Per-conversation override of the model's context size; null = use the server default. */
  private Integer numCtx;

  /** JSON-encoded list of stop sequences for this conversation; null when none are set. */
  @Column(columnDefinition = "text")
  private String stopSequences;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("seq ASC")
  private List<ChatMessageEntity> messages = new ArrayList<>();

  public Conversation() {}

  public Conversation(final String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(final String title) {
    this.title = title;
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

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(final String summary) {
    this.summary = summary;
  }

  public int getSummarizedCount() {
    return summarizedCount;
  }

  public void setSummarizedCount(final int summarizedCount) {
    this.summarizedCount = summarizedCount;
  }

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(final Double temperature) {
    this.temperature = temperature;
  }

  public Integer getNumCtx() {
    return numCtx;
  }

  public void setNumCtx(final Integer numCtx) {
    this.numCtx = numCtx;
  }

  public String getStopSequences() {
    return stopSequences;
  }

  public void setStopSequences(final String stopSequences) {
    this.stopSequences = stopSequences;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(final Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(final Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<ChatMessageEntity> getMessages() {
    return messages;
  }

  public void setMessages(final List<ChatMessageEntity> messages) {
    this.messages = messages;
  }
}

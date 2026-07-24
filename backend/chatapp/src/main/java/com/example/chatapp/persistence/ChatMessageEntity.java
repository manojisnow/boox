package com.example.chatapp.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A single message belonging to a {@link Conversation}, in Ollama {role, content} shape. */
@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  /** Ordering within the conversation. */
  @Column(nullable = false)
  private int seq;

  @Column(nullable = false)
  private String role;

  @Column(columnDefinition = "text")
  private String content;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  public ChatMessageEntity() {}

  public ChatMessageEntity(
      final Conversation conversation, final int seq, final String role, final String content) {
    this.conversation = conversation;
    this.seq = seq;
    this.role = role;
    this.content = content;
  }

  public Long getId() {
    return id;
  }

  public void setId(final Long id) {
    this.id = id;
  }

  public Conversation getConversation() {
    return conversation;
  }

  public void setConversation(final Conversation conversation) {
    this.conversation = conversation;
  }

  public int getSeq() {
    return seq;
  }

  public void setSeq(final int seq) {
    this.seq = seq;
  }

  public String getRole() {
    return role;
  }

  public void setRole(final String role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(final Instant createdAt) {
    this.createdAt = createdAt;
  }
}

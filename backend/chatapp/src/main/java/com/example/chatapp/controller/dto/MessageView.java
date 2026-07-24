package com.example.chatapp.controller.dto;

/** A single message for rendering a resumed conversation. */
public final class MessageView {

  private final String role;
  private final String content;

  public MessageView(final String role, final String content) {
    this.role = role;
    this.content = content;
  }

  public String getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }
}

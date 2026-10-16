package com.example.chatapp.controller.dto;

import java.util.List;

/** A single message for rendering a resumed conversation. */
public final class MessageView {

  private Long id;

  public Long getId() {
    return id;
  }

  public MessageView(Long id, String role, String content, List<String> images) {
    this(role, content, images);
    this.id = id;
  }

  private final String role;
  private final String content;
  private final List<String> images;

  public MessageView(final String role, final String content, final List<String> images) {
    this.role = role;
    this.content = content;
    this.images = images == null ? List.of() : images;
  }

  public String getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public List<String> getImages() {
    return images;
  }
}

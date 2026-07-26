package com.example.chatapp.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("PMD.UnnecessaryConstructor")
public class SendMessageRequest {
  private static final int MAX_IMAGES = 4;
  // ~7MB raw per image after base64's ~33% size overhead.
  private static final int MAX_IMAGE_BASE64_LENGTH = 9_500_000;

  @NotBlank private String message;
  @NotBlank private String server;
  @NotBlank private String model;
  @NotBlank private String sessionId;
  @NotNull private Boolean stream;
  private String systemPrompt;

  @Size(max = MAX_IMAGES)
  private List<@Size(max = MAX_IMAGE_BASE64_LENGTH) String> images;

  public SendMessageRequest() {}

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
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

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public Boolean getStream() {
    return stream;
  }

  public void setStream(final boolean stream) {
    this.stream = stream;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public List<String> getImages() {
    return images == null ? null : List.copyOf(images);
  }

  public void setImages(final List<String> images) {
    this.images = images == null ? null : new ArrayList<>(images);
  }
}

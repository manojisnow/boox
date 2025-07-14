package com.example.chatapp.controller;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@SuppressWarnings("PMD.UnnecessaryConstructor")
public class SendMessageRequest {
  @NotBlank private String message;
  @NotBlank private String model;
  @NotBlank private String sessionId;
  @NotNull private Boolean stream;

  public SendMessageRequest() {}

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
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
}

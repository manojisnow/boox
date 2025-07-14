package com.example.chatapp.controller;

import javax.validation.constraints.NotBlank;

@SuppressWarnings("PMD.UnnecessaryConstructor")
public class ResetContextRequest {
  @NotBlank private String sessionId;

  public ResetContextRequest() {}

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }
}

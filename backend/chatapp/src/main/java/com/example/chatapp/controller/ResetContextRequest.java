package com.example.chatapp.controller;

import javax.validation.constraints.NotBlank;

@SuppressWarnings("PMD.UnnecessaryConstructor")
public class ResetContextRequest {
  @NotBlank private String server;
  @NotBlank private String sessionId;

  public ResetContextRequest() {}

  public String getServer() {
    return server;
  }

  public void setServer(final String server) {
    this.server = server;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }
}

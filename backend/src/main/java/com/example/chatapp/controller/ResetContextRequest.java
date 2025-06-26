package com.example.chatapp.controller;

import javax.validation.constraints.NotBlank;

public class ResetContextRequest {
    @NotBlank
    private String server;
    @NotBlank
    private String sessionId;

    public String getServer() { return server; }
    public void setServer(String server) { this.server = server; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
} 
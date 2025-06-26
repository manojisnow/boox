package com.example.chatapp.controller;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class SendMessageRequest {
    @NotBlank
    private String message;
    @NotBlank
    private String server;
    @NotBlank
    private String model;
    @NotBlank
    private String sessionId;
    @NotNull
    private Boolean stream;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getServer() { return server; }
    public void setServer(String server) { this.server = server; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }
} 
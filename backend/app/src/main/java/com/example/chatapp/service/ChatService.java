package com.example.chatapp.service;

import com.example.chatapp.controller.ResetContextRequest;
import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
  private final Map<String, ChatEngine> engines;

  @Autowired
  public ChatService(Map<String, ChatEngine> engines) {
    this.engines = new HashMap<>(engines);
  }

  public List<String> getServers() {
    LOGGER.debug("Fetching list of servers");
    return new ArrayList<>(engines.keySet());
  }

  public List<ModelInfo> getModels(final String server) {
    final ChatEngine engine = engines.get(server);
    if (engine != null) {
      LOGGER.debug("Fetching models for server: {}", server);
      return engine.getModels();
    }
    LOGGER.warn("Requested models for unknown server: {}", server);
    return Collections.emptyList();
  }

  public Map<String, String> sendMessage(final SendMessageRequest request) {
    final String message = request.getMessage();
    final String server = request.getServer();
    final String model = request.getModel();
    final String sessionId = request.getSessionId();
    final boolean stream = request.getStream() != null && request.getStream();
    final ChatEngine engine = engines.get(server);
    if (engine != null) {
      LOGGER.info(
          "Sending message to server: {}, model: {}, session: {}", server, model, sessionId);
      return engine.sendMessage(message, model, sessionId, stream);
    }
    LOGGER.warn("Attempted to send message to unknown server: {}", server);
    return Map.of("role", "assistant", "content", "(unknown server)");
  }

  public void resetContext(final ResetContextRequest request) {
    final String server = request.getServer();
    final String sessionId = request.getSessionId();
    final ChatEngine engine = engines.get(server);
    if (engine != null) {
      LOGGER.info("Resetting context for server: {}, session: {}", server, sessionId);
      engine.resetContext(sessionId);
    } else {
      LOGGER.warn("Attempted to reset context for unknown server: {}", server);
    }
  }

  public String searchWeb(final String query) {
    LOGGER.info("Performing web search for query: {}", query);
    // Logic to perform a web search using DuckDuckGo
    return "Search results for query: " + query;
  }
}

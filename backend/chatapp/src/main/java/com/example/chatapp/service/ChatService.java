package com.example.chatapp.service;

import com.example.chatapp.controller.ResetContextRequest;
import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.engine.ChatContextService;
import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Spring-managed beans are not exposed outside this class; safe for DI usage.")
public class ChatService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
  private final Map<String, ChatEngine> engines;
  private final ChatContextService chatContextService;

  @Autowired
  public ChatService(
      final Map<String, ChatEngine> engines, final ChatContextService chatContextService) {
    this.engines = new HashMap<>(engines);
    this.chatContextService = chatContextService;
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
    if (request.getSystemPrompt() != null) {
      chatContextService.setSystemPrompt(sessionId, request.getSystemPrompt());
    }
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

  public void streamMessage(final SendMessageRequest request, final SseEmitter emitter) {
    final String server = request.getServer();
    if (request.getSystemPrompt() != null) {
      chatContextService.setSystemPrompt(request.getSessionId(), request.getSystemPrompt());
    }
    final ChatEngine engine = engines.get(server);
    if (engine != null) {
      LOGGER.info(
          "Streaming message to server: {}, model: {}, session: {}",
          server,
          request.getModel(),
          request.getSessionId());
      engine.streamMessage(
          request.getMessage(), request.getModel(), request.getSessionId(), emitter);
    } else {
      LOGGER.warn("Attempted to stream message to unknown server: {}", server);
      try {
        emitter.send(SseEmitter.event().data("(unknown server)"));
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
      } catch (Exception e) {
        emitter.completeWithError(e);
      }
    }
  }
}

package com.example.chatapp.service;

import com.example.chatapp.agent.AgentRunRepository;
import com.example.chatapp.agent.AgentRunStatus;
import com.example.chatapp.controller.ResetContextRequest;
import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.engine.ChatContextService;
import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.GenerationConfig;
import com.example.chatapp.engine.ModelInfo;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Spring-managed beans are not exposed outside this class; safe for DI usage.")
public class ChatService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
  private final Map<String, ChatEngine> engines;
  private final ChatContextService chatContextService;
  private final AgentRunRepository agentRuns;
  private final Map<String, String> executionOwners =
      new java.util.concurrent.ConcurrentHashMap<>();
  @Autowired private com.example.chatapp.context.ExecutionCoordinator coordinator;
  @Autowired private com.example.chatapp.context.PersonalizationResolver personalization;
  @Autowired private com.example.chatapp.memory.CurrentUserResolver users;
  @Autowired private com.example.chatapp.memory.MemoryService memory;

  @org.springframework.beans.factory.annotation.Value("${ollama.context.max-tokens:3000}")
  private int askInputCeiling = 3000;

  @org.springframework.beans.factory.annotation.Value("${ollama.context.num-ctx:8192}")
  private int askContextSize = 8192;

  @Autowired
  public ChatService(
      final Map<String, ChatEngine> engines,
      final ChatContextService chatContextService,
      final AgentRunRepository agentRuns) {
    this.engines = new HashMap<>(engines);
    this.chatContextService = chatContextService;
    this.agentRuns = agentRuns;
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

  public Map<String, Object> sendMessage(final SendMessageRequest request) {
    String owner = users.currentUserId();
    String token = reserve(request);
    try {
      startContext(request, owner, token);
      Map<String, Object> result = new HashMap<>(sendReserved(request));
      result.put(
          "contextUsed",
          com.example.chatapp.context.PersonalizationContext.current().contextUsed());
      return result;
    } finally {
      com.example.chatapp.context.PersonalizationContext.clear();
      coordinator.finish(owner, request.getSessionId(), token);
      executionOwners.remove(token);
    }
  }

  private Map<String, String> sendReserved(final SendMessageRequest request) {
    final String message = request.getMessage();
    final String server = request.getServer();
    final String model = request.getModel();
    final String sessionId = request.getSessionId();
    final boolean stream = request.isStream() != null && request.isStream();
    final ChatEngine engine = engines.get(server);
    if (engine != null) {
      LOGGER.info(
          "Sending message to server: {}, model: {}, session: {}", server, model, sessionId);
      final List<String> images = request.getImages();
      return engine.sendMessage(
          message, images == null ? List.of() : images, model, sessionId, stream);
    }
    LOGGER.warn("Attempted to send message to unknown server: {}", server);
    return Map.of("role", "assistant", "content", "(unknown server)");
  }

  public void resetContext(final ResetContextRequest request) {
    String owner = users.currentUserId();
    coordinator.withOwner(
        owner,
        () -> {
          assertConversationIdle(request.getSessionId());
          ChatEngine engine = engines.get(request.getServer());
          if (engine != null) engine.resetContext(request.getSessionId());
          return null;
        });
  }

  public String reserve(final SendMessageRequest request) {
    String owner = users.currentUserId();
    return coordinator.withOwner(
        owner,
        () -> {
          assertConversationIdle(request.getSessionId());
          chatContextService.setMetadata(
              request.getSessionId(), request.getServer(), request.getModel());
          if (request.getSystemPrompt() != null)
            chatContextService.setSystemPrompt(request.getSessionId(), request.getSystemPrompt());
          applyGenerationConfig(request, request.getSessionId());
          String token = coordinator.reserve(owner, request.getSessionId());
          executionOwners.put(token, owner);
          return token;
        });
  }

  public void release(final SendMessageRequest request, final String token) {
    String owner = executionOwners.remove(token);
    if (owner != null) coordinator.finish(owner, request.getSessionId(), token);
  }

  public Map<String, Object> executionStatus(String conversation) {
    memory.conversationSettings(conversation);
    return coordinator.status(users.currentUserId(), conversation);
  }

  private void startContext(SendMessageRequest request, String owner, String token) {
    com.example.chatapp.context.PersonalizationContext.guard(
        () -> coordinator.check(owner, request.getSessionId(), token));
    GenerationConfig config = chatContextService.getGenerationConfig(request.getSessionId());
    int capacity =
        config != null && config.getNumCtx() != null
            ? config.getNumCtx()
            : askContextSize > 0 ? askContextSize : 8192;
    int budget =
        Math.max(0, Math.min(askInputCeiling, capacity - Math.min(1024, capacity / 4) - 128));
    com.example.chatapp.context.PersonalizationContext.install(
        personalization.resolve(
            owner,
            request.getSessionId(),
            token,
            request.getMessage(),
            request.getSelectedMemoryIds(),
            budget));
    memory.recordContextUsed(
        request.getSessionId(),
        token,
        com.example.chatapp.context.PersonalizationContext.current().contextUsed());
  }

  public void streamReserved(
      final SendMessageRequest request, final SseEmitter emitter, final String token) {
    String owner = executionOwners.get(token);
    if (owner == null)
      throw new java.util.concurrent.CancellationException("Execution was not accepted");
    try {
      startContext(request, owner, token);
      try {
        emitter.send(
            SseEmitter.event()
                .name("context_used")
                .data(com.example.chatapp.context.PersonalizationContext.current().contextUsed()));
      } catch (java.io.IOException | RuntimeException disconnected) {
        LOGGER.debug("Context metadata transport closed");
      }
      streamCore(request, emitter);
    } catch (RuntimeException error) {
      try {
        emitter.send(
            SseEmitter.event().data("Unable to start this request: " + error.getMessage()));
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
      } catch (java.io.IOException | RuntimeException disconnected) {
        emitter.completeWithError(error);
      }
    } finally {
      com.example.chatapp.context.PersonalizationContext.clear();
      coordinator.finish(owner, request.getSessionId(), token);
      executionOwners.remove(token);
    }
  }

  public void streamMessage(final SendMessageRequest request, final SseEmitter emitter) {
    String token = reserve(request);
    streamReserved(request, emitter, token);
  }

  private void streamCore(final SendMessageRequest request, final SseEmitter emitter) {
    final String server = request.getServer();
    final ChatEngine engine = engines.get(server);
    if (engine != null) {
      LOGGER.info(
          "Streaming message to server: {}, model: {}, session: {}",
          server,
          request.getModel(),
          request.getSessionId());
      final List<String> images = request.getImages();
      engine.streamMessage(
          request.getMessage(),
          images == null ? List.of() : images,
          request.getModel(),
          request.getSessionId(),
          emitter);
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

  public void assertConversationIdle(final String sessionId) {
    coordinator.assertIdle(users.currentUserId(), sessionId);
    if (agentRuns.existsByConversationIdAndStatusIn(
        sessionId,
        List.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING, AgentRunStatus.CANCEL_REQUESTED))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "An agent task is active in this conversation. Wait for it or cancel it first.");
    }
  }

  /**
   * Replaces generation overrides when explicitly requested, including clearing every field. Legacy
   * requests with overrides still apply them; messages without settings preserve the saved config.
   */
  private void applyGenerationConfig(final SendMessageRequest request, final String sessionId) {
    if (request.isReplaceGenerationConfig()
        || request.getTemperature() != null
        || request.getNumCtx() != null
        || (request.getStopSequences() != null && !request.getStopSequences().isEmpty())) {
      chatContextService.setGenerationConfig(
          sessionId,
          new GenerationConfig(
              request.getTemperature(), request.getNumCtx(), request.getStopSequences()));
    }
  }
}

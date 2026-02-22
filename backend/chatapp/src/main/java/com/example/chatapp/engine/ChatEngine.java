package com.example.chatapp.engine;

import java.util.List;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatEngine {
  List<ModelInfo> getModels();

  Map<String, String> sendMessage(String message, String model, String sessionId, boolean stream);

  /** Stream a message response via SSE. Default implementation falls back to sendMessage. */
  default void streamMessage(String message, String model, String sessionId, SseEmitter emitter) {
    try {
      Map<String, String> result = sendMessage(message, model, sessionId, false);
      emitter.send(SseEmitter.event().data(result.get("content")));
      emitter.send(SseEmitter.event().data("[DONE]"));
      emitter.complete();
    } catch (Exception e) {
      emitter.completeWithError(e);
    }
  }

  void resetContext(String sessionId);
}

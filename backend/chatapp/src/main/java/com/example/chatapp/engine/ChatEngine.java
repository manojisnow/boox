package com.example.chatapp.engine;

import java.util.List;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatEngine {
  List<ModelInfo> getModels();

  Map<String, String> sendMessage(
      String message, List<String> images, String model, String sessionId, boolean stream);

  /** Text-only convenience overload. Equivalent to passing an empty image list. */
  default Map<String, String> sendMessage(
      final String message, final String model, final String sessionId, final boolean stream) {
    return sendMessage(message, List.of(), model, sessionId, stream);
  }

  /** Stream a message response via SSE. Default implementation falls back to sendMessage. */
  default void streamMessage(
      final String message,
      final List<String> images,
      final String model,
      final String sessionId,
      final SseEmitter emitter) {
    try {
      final Map<String, String> result = sendMessage(message, images, model, sessionId, false);
      emitter.send(SseEmitter.event().data(result.get("content")));
      emitter.send(SseEmitter.event().data("[DONE]"));
      emitter.complete();
    } catch (Exception e) {
      emitter.completeWithError(e);
    }
  }

  /** Text-only convenience overload. Equivalent to passing an empty image list. */
  default void streamMessage(
      final String message, final String model, final String sessionId, final SseEmitter emitter) {
    streamMessage(message, List.of(), model, sessionId, emitter);
  }

  void resetContext(String sessionId);
}

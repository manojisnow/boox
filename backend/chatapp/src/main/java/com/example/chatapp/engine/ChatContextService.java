package com.example.chatapp.engine;

import java.util.List;
import java.util.Map;

public interface ChatContextService {
  List<Map<String, String>> getContext(String sessionId);

  void addMessage(String sessionId, String role, String content);

  void setSystemPrompt(String sessionId, String systemPrompt);

  String getSystemPrompt(String sessionId);

  void resetContext(String sessionId);

  /**
   * Records the server/model a conversation is using, for display and resume. No-op by default so
   * non-persistent implementations need not track it.
   */
  default void setMetadata(String sessionId, String server, String model) {
    // no-op
  }
}

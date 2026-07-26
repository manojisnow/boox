package com.example.chatapp.engine;

import java.util.List;
import java.util.Map;

public interface ChatContextService {
  /** Each message is at least {@code role}/{@code content}; may also carry {@code images}. */
  List<Map<String, Object>> getContext(String sessionId);

  /**
   * Adds a text-only message. Equivalent to {@code addMessage(sessionId, role, content,
   * List.of())}.
   */
  default void addMessage(String sessionId, String role, String content) {
    addMessage(sessionId, role, content, List.of());
  }

  /** Adds a message, optionally carrying base64-encoded images (no {@code data:} prefix). */
  void addMessage(String sessionId, String role, String content, List<String> images);

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

  /** Running summary of older turns already folded out of the context window; null if none. */
  default String getSummary(String sessionId) {
    return null;
  }

  /** How many of the oldest messages are already represented by {@link #getSummary}. */
  default int getSummarizedCount(String sessionId) {
    return 0;
  }

  /** Persists the running summary and the count of messages it now covers. No-op by default. */
  default void setSummaryState(String sessionId, String summary, int summarizedCount) {
    // no-op
  }
}

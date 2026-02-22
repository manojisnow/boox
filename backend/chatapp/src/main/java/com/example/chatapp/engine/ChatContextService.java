package com.example.chatapp.engine;

import java.util.List;
import java.util.Map;

public interface ChatContextService {
  List<Map<String, String>> getContext(String sessionId);

  void addMessage(String sessionId, String role, String content);

  void setSystemPrompt(String sessionId, String systemPrompt);

  String getSystemPrompt(String sessionId);

  void resetContext(String sessionId);
}

package com.example.chatapp.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@SuppressWarnings("PMD.AtLeastOneConstructor")
@Service
public class InMemoryChatContextService implements ChatContextService {
  // TODO: Both maps grow unbounded. Add TTL-based eviction (e.g. Caffeine or a scheduled
  //       cleanup) before deploying to a multi-user or long-running environment.
  private final Map<String, List<Map<String, String>>> chatContexts = new ConcurrentHashMap<>();
  private final Map<String, String> systemPrompts = new ConcurrentHashMap<>();

  @Override
  public List<Map<String, String>> getContext(final String sessionId) {
    return chatContexts.computeIfAbsent(
        sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
  }

  @Override
  public void addMessage(final String sessionId, final String role, final String content) {
    getContext(sessionId).add(Map.of("role", role, "content", content));
  }

  @Override
  public void setSystemPrompt(final String sessionId, final String systemPrompt) {
    if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
      systemPrompts.put(sessionId, systemPrompt.trim());
    } else {
      systemPrompts.remove(sessionId);
    }
  }

  @Override
  public String getSystemPrompt(final String sessionId) {
    return systemPrompts.get(sessionId);
  }

  @Override
  public void resetContext(final String sessionId) {
    chatContexts.remove(sessionId);
    systemPrompts.remove(sessionId);
  }
}

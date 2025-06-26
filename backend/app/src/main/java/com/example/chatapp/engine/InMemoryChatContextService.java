package com.example.chatapp.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@SuppressWarnings("PMD.AtLeastOneConstructor")
@Service
public class InMemoryChatContextService implements ChatContextService {
  private final Map<String, List<Map<String, String>>> chatContexts = new ConcurrentHashMap<>();

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
  public void resetContext(final String sessionId) {
    chatContexts.remove(sessionId);
  }
}

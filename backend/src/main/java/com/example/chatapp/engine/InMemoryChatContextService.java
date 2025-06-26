package com.example.chatapp.engine;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryChatContextService implements ChatContextService {
    private final Map<String, List<Map<String, String>>> chatContexts = new ConcurrentHashMap<>();

    @Override
    public List<Map<String, String>> getContext(String sessionId) {
        return chatContexts.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    @Override
    public void addMessage(String sessionId, String role, String content) {
        getContext(sessionId).add(Map.of("role", role, "content", content));
    }

    @Override
    public void resetContext(String sessionId) {
        chatContexts.remove(sessionId);
    }
}


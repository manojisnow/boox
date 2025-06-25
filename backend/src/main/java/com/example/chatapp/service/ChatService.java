package com.example.chatapp.service;

import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChatService {
    private final Map<String, ChatEngine> engines;

    @Autowired
    public ChatService(Map<String, ChatEngine> engines) {
        this.engines = engines;
    }

    public List<String> getServers() {
        return new ArrayList<>(engines.keySet());
    }

    public List<ModelInfo> getModels(String server) {
        ChatEngine engine = engines.get(server);
        if (engine != null) {
            return engine.getModels();
        }
        return Collections.emptyList();
    }

    public Map<String, String> sendMessage(String message, String server, String model, String sessionId, boolean stream) {
        ChatEngine engine = engines.get(server);
        if (engine != null) {
            return engine.sendMessage(message, model, sessionId, stream);
        }
        return Map.of("role", "assistant", "content", "(unknown server)");
    }

    public void resetContext(String server, String sessionId) {
        ChatEngine engine = engines.get(server);
        if (engine != null) {
            engine.resetContext(sessionId);
        }
    }

    public String searchWeb(String query) {
        // Logic to perform a web search using DuckDuckGo
        return "Search results for query: " + query;
    }
}


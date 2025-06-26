package com.example.chatapp.service;

import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    private final Map<String, ChatEngine> engines;

    @Autowired
    public ChatService(Map<String, ChatEngine> engines) {
        this.engines = engines;
    }

    public List<String> getServers() {
        logger.debug("Fetching list of servers");
        return new ArrayList<>(engines.keySet());
    }

    public List<ModelInfo> getModels(String server) {
        ChatEngine engine = engines.get(server);
        if (engine != null) {
            logger.debug("Fetching models for server: {}", server);
            return engine.getModels();
        }
        logger.warn("Requested models for unknown server: {}", server);
        return Collections.emptyList();
    }

    public Map<String, String> sendMessage(String message, String server, String model, String sessionId, boolean stream) {
        ChatEngine engine = engines.get(server);
        if (engine != null) {
            logger.info("Sending message to server: {}, model: {}, session: {}", server, model, sessionId);
            return engine.sendMessage(message, model, sessionId, stream);
        }
        logger.warn("Attempted to send message to unknown server: {}", server);
        return Map.of("role", "assistant", "content", "(unknown server)");
    }

    public void resetContext(String server, String sessionId) {
        ChatEngine engine = engines.get(server);
        if (engine != null) {
            logger.info("Resetting context for server: {}, session: {}", server, sessionId);
            engine.resetContext(sessionId);
        } else {
            logger.warn("Attempted to reset context for unknown server: {}", server);
        }
    }

    public String searchWeb(String query) {
        logger.info("Performing web search for query: {}", query);
        // Logic to perform a web search using DuckDuckGo
        return "Search results for query: " + query;
    }
}


package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.chatapp.engine.InMemoryChatContextService;
import com.example.chatapp.service.ChatService;
import com.example.chatapp.engine.OllamaChatEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

public class ChatServiceTest {
    private ChatService chatService;

    @BeforeEach
    public void setUp() {
        // Only OllamaChatEngine for this test
        OllamaChatEngine ollamaEngine = new OllamaChatEngine(new RestTemplate(), new InMemoryChatContextService());
        chatService = new ChatService(Map.of("ollama", ollamaEngine));
    }

    @Test
    public void testGetServersContainsOllama() {
        List<String> servers = chatService.getServers();
        assertTrue(servers.contains("ollama"));
    }
}
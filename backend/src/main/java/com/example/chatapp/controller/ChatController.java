package com.example.chatapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.example.chatapp.engine.ModelInfo;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final com.example.chatapp.service.ChatService chatService;

    @Autowired
    public ChatController(com.example.chatapp.service.ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/servers")
    public List<String> getServers() {
        return chatService.getServers();
    }

    @GetMapping("/models")
    public List<String> getModels(@RequestParam String server) {
        List<ModelInfo> modelInfos = chatService.getModels(server);
        return modelInfos.stream().map(ModelInfo::getName).toList();
    }

    @PostMapping("/send")
    public Map<String, String> sendMessage(@RequestBody Map<String, Object> payload) {
        String message = (String) payload.get("message");
        String server = (String) payload.get("server");
        String model = (String) payload.get("model");
        String sessionId = (String) payload.get("sessionId");
        boolean stream = payload.get("stream") != null && Boolean.TRUE.equals(payload.get("stream"));
        return chatService.sendMessage(message, server, model, sessionId, stream);
    }

    @GetMapping("/search")
    public String searchWeb(@RequestParam String query) {
        return chatService.searchWeb(query);
    }

    @PostMapping("/reset-context")
    public void resetContext(@RequestBody Map<String, String> payload) {
        String server = payload.get("server");
        String sessionId = payload.get("sessionId");
        chatService.resetContext(server, sessionId);
    }
}

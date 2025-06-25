package com.example.chatapp.engine;

import java.util.List;
import java.util.Map;

public interface ChatEngine {
    List<ModelInfo> getModels();
    Map<String, String> sendMessage(String message, String model, String sessionId, boolean stream);
    void resetContext(String sessionId);
}


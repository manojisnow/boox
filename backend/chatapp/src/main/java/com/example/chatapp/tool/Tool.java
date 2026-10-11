package com.example.chatapp.tool;

import java.util.Map;

/** Interface for tools that the AI can invoke during a conversation. */
@SuppressWarnings("PMD.ShortClassName")
public interface Tool {
  String getName();

  String getDescription();

  Map<String, Object> getParameterSchema();

  String execute(Map<String, Object> arguments);

  /** Trusted runtime context; models cannot choose the conversation or model identifier. */
  default String execute(Map<String, Object> arguments, String conversationId, String model) {
    return execute(arguments);
  }
}

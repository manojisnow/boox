package com.example.chatapp.tool;

import java.util.Map;

/** Interface for tools that the AI can invoke during a conversation. */
public interface Tool {
  String getName();

  String getDescription();

  Map<String, Object> getParameterSchema();

  String execute(Map<String, Object> arguments);
}

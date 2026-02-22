package com.example.chatapp.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Registry that discovers and manages all Tool beans. */
@Component
public class ToolRegistry {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);
  private final Map<String, Tool> tools;

  @Autowired
  public ToolRegistry(final List<Tool> toolList) {
    this.tools = new HashMap<>();
    for (final Tool tool : toolList) {
      this.tools.put(tool.getName(), tool);
      LOGGER.info("Registered tool: {}", tool.getName());
    }
  }

  /** Returns tool definitions formatted for the Ollama API tools parameter. */
  public List<Map<String, Object>> getToolDefinitions() {
    return tools.values().stream()
        .map(
            tool -> {
              final Map<String, Object> def = new HashMap<>();
              def.put("type", "function");
              final Map<String, Object> function = new HashMap<>();
              function.put("name", tool.getName());
              function.put("description", tool.getDescription());
              function.put("parameters", tool.getParameterSchema());
              def.put("function", function);
              return def;
            })
        .collect(Collectors.toList());
  }

  /** Execute a tool by name with the given arguments. */
  public String executeTool(final String name, final Map<String, Object> arguments) {
    final Tool tool = tools.get(name);
    if (tool == null) {
      LOGGER.warn("Tool not found: {}", name);
      return "Error: Tool '" + name + "' not found.";
    }
    LOGGER.info("Executing tool: {} with args: {}", name, arguments);
    try {
      return tool.execute(arguments);
    } catch (Exception e) {
      LOGGER.error("Tool execution failed: {}", e.getMessage(), e);
      return "Error executing tool '" + name + "': " + e.getMessage();
    }
  }

  /** Returns a list of available tool info for the frontend. */
  public List<Map<String, String>> getAvailableTools() {
    final List<Map<String, String>> result = new ArrayList<>();
    for (final Tool tool : tools.values()) {
      result.add(Map.of("name", tool.getName(), "description", tool.getDescription()));
    }
    return result;
  }

  public boolean hasTools() {
    return !tools.isEmpty();
  }
}

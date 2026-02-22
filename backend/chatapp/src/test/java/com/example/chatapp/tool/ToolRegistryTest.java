package com.example.chatapp.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  private ToolRegistry registry;
  private Tool mockTool;

  @BeforeEach
  void setUp() {
    mockTool =
        new Tool() {
          @Override
          public String getName() {
            return "test_tool";
          }

          @Override
          public String getDescription() {
            return "A test tool";
          }

          @Override
          public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
          }

          @Override
          public String execute(final Map<String, Object> arguments) {
            return "executed with: " + arguments;
          }
        };
    registry = new ToolRegistry(List.of(mockTool));
  }

  @Test
  void hasTools_returnsTrue_whenToolsRegistered() {
    assertTrue(registry.hasTools());
  }

  @Test
  void hasTools_returnsFalse_whenNoTools() {
    ToolRegistry emptyRegistry = new ToolRegistry(List.of());
    assertFalse(emptyRegistry.hasTools());
  }

  @Test
  void getToolDefinitions_returnsCorrectFormat() {
    List<Map<String, Object>> defs = registry.getToolDefinitions();
    assertEquals(1, defs.size());
    assertEquals("function", defs.get(0).get("type"));
    Map<String, Object> function = (Map<String, Object>) defs.get(0).get("function");
    assertEquals("test_tool", function.get("name"));
    assertEquals("A test tool", function.get("description"));
  }

  @Test
  void executeTool_executesNamedTool() {
    String result = registry.executeTool("test_tool", Map.of("key", "value"));
    assertTrue(result.contains("executed with"));
  }

  @Test
  void executeTool_returnsError_whenToolNotFound() {
    String result = registry.executeTool("nonexistent", Map.of());
    assertTrue(result.contains("not found"));
  }

  @Test
  void getAvailableTools_returnsToolInfo() {
    List<Map<String, String>> tools = registry.getAvailableTools();
    assertEquals(1, tools.size());
    assertEquals("test_tool", tools.get(0).get("name"));
    assertEquals("A test tool", tools.get(0).get("description"));
  }
}

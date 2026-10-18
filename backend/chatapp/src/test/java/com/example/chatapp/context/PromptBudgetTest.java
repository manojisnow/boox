package com.example.chatapp.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class PromptBudgetTest {
  private Map<String, Object> message(String role, String text) {
    return Map.of("role", role, "content", text);
  }

  @Test
  void dropsWholeEarlierTurnsAndPreservesToolGroup() {
    Map<String, Object> call =
        Map.of(
            "role",
            "assistant",
            "content",
            "",
            "tool_calls",
            List.of(Map.of("function", "search")));
    List<Map<String, Object>> rows =
        List.of(
            message("system", "rules"),
            message("user", "old".repeat(1000)),
            message("assistant", "old answer"),
            message("user", "latest"),
            call,
            message("tool", "result"));
    List<Map<String, Object>> result = PromptBudget.fit(rows, List.of(), 2000, 256, 400);
    assertEquals(4, result.size());
    assertTrue(result.contains(call));
    assertEquals("tool", result.getLast().get("role"));
  }

  @Test
  void rejectsMandatoryOverflowAndAccountsImages() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PromptBudget.fit(
                List.of(message("user", "x".repeat(2000))), List.of(), 512, 128, 3000));
    assertTrue(
        PromptBudget.cost(Map.of("role", "user", "content", "hi", "images", List.of("data")))
            >= 768);
  }

  @Test
  void agentCapacityIsIndependentOfAskCeiling() {
    List<Map<String, Object>> rows =
        List.of(message("system", "rules"), message("user", "x".repeat(24000)));
    assertEquals(2, PromptBudget.fit(rows, List.of(), 16384, 2048, 0).size());
    assertThrows(
        IllegalArgumentException.class, () -> PromptBudget.fit(rows, List.of(), 8192, 1024, 3000));
  }
}

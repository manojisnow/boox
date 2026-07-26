package com.example.chatapp.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextWindowManagerTest {

  private final ContextWindowManager manager = new ContextWindowManager();

  private static Map<String, Object> msg(final String role, final String content) {
    return Map.of("role", role, "content", content);
  }

  private static Map<String, Object> msgWithImages(
      final String role, final String content, final int imageCount) {
    return Map.of(
        "role", role, "content", content, "images", Collections.nCopies(imageCount, "b64"));
  }

  @Test
  void estimateTokens_isRoughlyCharsOverFour() {
    assertEquals(1, manager.estimateTokens(null));
    assertEquals(1, manager.estimateTokens(""));
    assertEquals(25, manager.estimateTokens("x".repeat(100)));
  }

  @Test
  void split_emptyList_returnsEmpty() {
    ContextWindowManager.Split split = manager.split(List.of(), 100);
    assertTrue(split.getWindow().isEmpty());
    assertTrue(split.getDropped().isEmpty());
  }

  @Test
  void split_keepsRecentMessagesWithinBudget() {
    List<Map<String, Object>> messages =
        List.of(
            msg("user", "a".repeat(100)),
            msg("assistant", "b".repeat(100)),
            msg("user", "c".repeat(100)),
            msg("assistant", "d".repeat(100)));
    // Each message is ~26 tokens; a 60-token budget keeps the last couple.
    ContextWindowManager.Split split = manager.split(messages, 60);
    assertFalse(split.getWindow().isEmpty());
    assertTrue(split.getWindow().size() < messages.size());
    assertEquals(messages.size(), split.getDropped().size() + split.getWindow().size());
    assertEquals("user", split.getWindow().get(0).get("role"));
  }

  @Test
  void split_windowStartsAtUserMessage() {
    List<Map<String, Object>> messages =
        List.of(
            msg("user", "q1"),
            msg("assistant", "a1"),
            msg("tool", "result"),
            msg("assistant", "a2"),
            msg("user", "q2"),
            msg("assistant", "a3"));
    ContextWindowManager.Split split = manager.split(messages, 100000);
    assertEquals("user", split.getWindow().get(0).get("role"));
    assertEquals(messages.size(), split.getWindow().size());
  }

  @Test
  void split_orphanedLeadingNonUser_isDropped() {
    List<Map<String, Object>> messages =
        List.of(msg("assistant", "leading"), msg("user", "q"), msg("assistant", "a"));
    ContextWindowManager.Split split = manager.split(messages, 100000);
    assertEquals("user", split.getWindow().get(0).get("role"));
    assertEquals(1, split.getDropped().size());
    assertEquals("assistant", split.getDropped().get(0).get("role"));
  }

  @Test
  void split_alwaysKeepsAtLeastTheLastMessage() {
    List<Map<String, Object>> messages = List.of(msg("user", "x".repeat(1000)));
    ContextWindowManager.Split split = manager.split(messages, 1);
    assertEquals(1, split.getWindow().size());
    assertTrue(split.getDropped().isEmpty());
  }

  @Test
  void split_imageBearingMessage_costsMoreTokens_soDropsEarlier() {
    // A short-text message with 2 images should cost enough to get dropped under a small
    // budget that would otherwise comfortably fit its text alone.
    List<Map<String, Object>> messages =
        List.of(msgWithImages("user", "look", 2), msg("user", "final"));
    ContextWindowManager.Split split = manager.split(messages, 50);
    assertEquals(1, split.getWindow().size());
    assertEquals("final", split.getWindow().get(0).get("content"));
    assertEquals(1, split.getDropped().size());
  }
}

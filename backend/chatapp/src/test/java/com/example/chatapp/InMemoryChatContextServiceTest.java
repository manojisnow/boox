package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;

import com.example.chatapp.engine.InMemoryChatContextService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryChatContextServiceTest {
  private InMemoryChatContextService service;

  @BeforeEach
  void setUp() {
    service = new InMemoryChatContextService();
  }

  @Test
  void getContext_returnsEmptyListInitially() {
    List<Map<String, String>> context = service.getContext("sid");
    assertNotNull(context);
    assertTrue(context.isEmpty());
  }

  @Test
  void addMessage_and_getContext() {
    service.addMessage("sid", "role", "content");
    List<Map<String, String>> context = service.getContext("sid");
    assertEquals(1, context.size());
    assertEquals("role", context.get(0).get("role"));
    assertEquals("content", context.get(0).get("content"));
  }

  @Test
  void resetContext_removesContext() {
    service.addMessage("sid", "role", "content");
    service.resetContext("sid");
    List<Map<String, String>> context = service.getContext("sid");
    assertTrue(context.isEmpty());
  }
}

package com.example.chatapp.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaChatContextService.class)
@ActiveProfiles("test")
class JpaChatContextServiceTest {

  @Autowired private JpaChatContextService service;
  @Autowired private ConversationRepository conversations;

  @Test
  void getContext_newSession_isEmptyAndCreatesConversation() {
    List<Map<String, Object>> context = service.getContext("s1");
    assertTrue(context.isEmpty());
    assertTrue(conversations.findById("s1").isPresent());
  }

  @Test
  void addMessage_persistsInOrder() {
    service.addMessage("s2", "user", "hi");
    service.addMessage("s2", "assistant", "hello");
    List<Map<String, Object>> context = service.getContext("s2");
    assertEquals(2, context.size());
    assertEquals("user", context.get(0).get("role"));
    assertEquals("hi", context.get(0).get("content"));
    assertEquals("assistant", context.get(1).get("role"));
  }

  @Test
  void addMessage_withImages_roundTripsThroughGetContext() {
    service.addMessage("s8", "user", "what is this", List.of("base64imgA", "base64imgB"));
    List<Map<String, Object>> context = service.getContext("s8");
    assertEquals(1, context.size());
    assertEquals(List.of("base64imgA", "base64imgB"), context.get(0).get("images"));
  }

  @Test
  void addMessage_withoutImages_omitsImagesKey() {
    service.addMessage("s9", "user", "hi");
    List<Map<String, Object>> context = service.getContext("s9");
    assertFalse(context.get(0).containsKey("images"));
  }

  @Test
  void addMessage_derivesTitleFromFirstUserMessage() {
    service.addMessage("s3", "user", "What is the capital of France?");
    assertEquals(
        "What is the capital of France?", conversations.findById("s3").orElseThrow().getTitle());
  }

  @Test
  void addMessage_longFirstMessage_truncatesTitle() {
    String longMessage = "x".repeat(200);
    service.addMessage("s4", "user", longMessage);
    String title = conversations.findById("s4").orElseThrow().getTitle();
    assertTrue(title.endsWith("…"));
    assertTrue(title.length() <= 61);
  }

  @Test
  void systemPrompt_setGetAndClear() {
    service.setSystemPrompt("s5", "  be terse  ");
    assertEquals("be terse", service.getSystemPrompt("s5"));
    service.setSystemPrompt("s5", "   ");
    assertNull(service.getSystemPrompt("s5"));
  }

  @Test
  void setMetadata_storesServerAndModel() {
    service.setMetadata("s6", "ollama", "qwen2.5:14b");
    Conversation conversation = conversations.findById("s6").orElseThrow();
    assertEquals("ollama", conversation.getServer());
    assertEquals("qwen2.5:14b", conversation.getModel());
  }

  @Test
  void summaryState_roundTrips() {
    service.addMessage("sum1", "user", "hi");
    assertNull(service.getSummary("sum1"));
    assertEquals(0, service.getSummarizedCount("sum1"));
    service.setSummaryState("sum1", "earlier summary", 3);
    assertEquals("earlier summary", service.getSummary("sum1"));
    assertEquals(3, service.getSummarizedCount("sum1"));
  }

  @Test
  void resetContext_clearsMessagesButKeepsConversation() {
    service.addMessage("s7", "user", "hi");
    service.setSystemPrompt("s7", "prompt");
    service.resetContext("s7");
    assertTrue(service.getContext("s7").isEmpty());
    assertNull(service.getSystemPrompt("s7"));
    assertTrue(conversations.findById("s7").isPresent());
  }
}

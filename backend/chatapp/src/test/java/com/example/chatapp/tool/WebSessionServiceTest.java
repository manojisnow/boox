package com.example.chatapp.tool;

import static org.junit.jupiter.api.Assertions.*;

import com.example.chatapp.persistence.Conversation;
import com.example.chatapp.persistence.ConversationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WebSessionService.class)
@ActiveProfiles("test")
class WebSessionServiceTest {
  @Autowired private WebSessionService sessions;
  @Autowired private ConversationRepository conversations;
  @Autowired private TestEntityManager entityManager;

  @Test
  void retainsRandomCorrelationAcrossCallsAndReloadsWithoutExposingConversationIds() {
    entityManager.persistAndFlush(new Conversation("private-name@example.org"));
    String id = sessions.sessionId("private-name@example.org");
    assertDoesNotThrow(() -> UUID.fromString(id));
    assertFalse(id.contains("private-name"));
    assertEquals(id, sessions.sessionId("private-name@example.org"));
    entityManager.clear();
    assertEquals(
        id, conversations.findById("private-name@example.org").orElseThrow().getWebSessionId());
    entityManager.persistAndFlush(new Conversation("other"));
    assertNotEquals(id, sessions.sessionId("other"));
    assertEquals(id, sessions.sessionId("private-name@example.org"));
  }

  @Test
  void rejectsMissingContextAndUnknownConversations() {
    assertThrows(IllegalArgumentException.class, () -> sessions.sessionId(null));
    assertThrows(IllegalArgumentException.class, () -> sessions.sessionId(" "));
    assertThrows(IllegalArgumentException.class, () -> sessions.sessionId("missing"));
  }
}

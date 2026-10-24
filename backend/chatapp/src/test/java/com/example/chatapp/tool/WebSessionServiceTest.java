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

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:sqlite:target/ullavox-web-session-migration-test.db",
      "spring.flyway.enabled=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  WebSessionService.class,
  com.example.chatapp.config.SchemaMigrationConfiguration.class,
  com.example.chatapp.memory.CurrentUserResolver.class,
  com.example.chatapp.context.ExecutionCoordinator.class
})
@ActiveProfiles("test")
class WebSessionServiceTest {
  @Autowired private WebSessionService sessions;
  @Autowired private ConversationRepository conversations;
  @Autowired private TestEntityManager entityManager;
  @Autowired private com.example.chatapp.memory.CurrentUserResolver users;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

  private Conversation owned(String id) {
    Conversation conversation = new Conversation(id);
    conversation.setOwnerId(users.currentUserId());
    return conversation;
  }

  @Test
  void retainsRandomCorrelationAcrossCallsAndReloadsWithoutExposingConversationIds() {
    entityManager.persistAndFlush(owned("private-name@example.org"));
    String id = sessions.sessionId("private-name@example.org");
    assertDoesNotThrow(() -> UUID.fromString(id));
    assertFalse(id.contains("private-name"));
    assertEquals(id, sessions.sessionId("private-name@example.org"));
    entityManager.clear();
    assertEquals(
        id, conversations.findById("private-name@example.org").orElseThrow().getWebSessionId());
    entityManager.persistAndFlush(owned("other"));
    assertNotEquals(id, sessions.sessionId("other"));
    assertEquals(id, sessions.sessionId("private-name@example.org"));
  }

  @Test
  void rejectsMissingContextAndUnknownConversations() {
    assertThrows(IllegalArgumentException.class, () -> sessions.sessionId(null));
    assertThrows(IllegalArgumentException.class, () -> sessions.sessionId(" "));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> sessions.sessionId("missing"));
  }

  @Test
  void foreignConversationCannotExposeOrCreateProviderSession() {
    String owner = java.util.UUID.randomUUID().toString();
    jdbc.update(
        "insert into local_user(id,created_at) values(?,?)",
        owner,
        java.time.Instant.now().toString());
    Conversation foreign = new Conversation("foreign-chat");
    foreign.setOwnerId(owner);
    entityManager.persistAndFlush(foreign);
    var error =
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> sessions.sessionId("foreign-chat"));
    assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, error.getStatusCode());
    assertNull(
        jdbc.queryForObject(
            "select web_session_id from conversation where id='foreign-chat'", String.class));
  }
}

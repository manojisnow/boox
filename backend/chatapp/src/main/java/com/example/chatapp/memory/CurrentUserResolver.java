package com.example.chatapp.memory;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the installation identity, independently of any future login mechanism. */
@Component
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring-managed dependencies are shared application services.")
public class CurrentUserResolver {
  private final JdbcTemplate jdbc;

  public CurrentUserResolver(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String currentUserId() {
    return jdbc.queryForObject("select local_user_id from installation where id = 1", String.class);
  }

  public void requireOwnedConversation(String id) {
    assertConversationAccessible(id, false);
  }

  public void assertConversationAccessible(String id, boolean allowMissing) {
    var owners =
        jdbc.queryForList("select owner_id from conversation where id = ?", String.class, id);
    if (owners.isEmpty() && allowMissing) return;
    if (owners.isEmpty() || !currentUserId().equals(owners.getFirst()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation unavailable");
  }
}

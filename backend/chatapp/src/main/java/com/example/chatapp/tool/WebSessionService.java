package com.example.chatapp.tool;

import com.example.chatapp.persistence.ConversationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists one random external correlation ID per conversation, including across restarts. */
@Service
public class WebSessionService {
  private final ConversationRepository conversations;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.example.chatapp.memory.CurrentUserResolver users;

  public WebSessionService(final ConversationRepository conversations) {
    this.conversations = conversations;
  }

  @Transactional
  public String sessionId(final String conversationId) {
    if (conversationId == null || conversationId.isBlank())
      throw new IllegalArgumentException("A conversation is required for web access.");
    if (users != null) users.requireOwnedConversation(conversationId);
    conversations.initializeWebSession(conversationId, UUID.randomUUID().toString());
    final String session = conversations.findWebSession(conversationId);
    if (session == null) throw new IllegalArgumentException("Conversation not found.");
    return session;
  }
}

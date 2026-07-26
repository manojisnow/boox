package com.example.chatapp.persistence;

import com.example.chatapp.controller.dto.ConversationSummary;
import com.example.chatapp.controller.dto.MessageView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/manage persisted conversations for the sidebar UI. */
@Service
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Injected Spring Data repositories are singleton beans; safe to retain.")
public class ConversationService {

  private static final String ROLE_USER = "user";
  private static final String ROLE_ASSISTANT = "assistant";

  private final ConversationRepository conversations;
  private final ChatMessageRepository messageRepository;

  public ConversationService(
      final ConversationRepository conversations, final ChatMessageRepository messageRepository) {
    this.conversations = conversations;
    this.messageRepository = messageRepository;
  }

  /** All conversations, most-recently-updated first. */
  @Transactional(readOnly = true)
  public List<ConversationSummary> list() {
    return conversations.findAllByOrderByUpdatedAtDesc().stream()
        .map(ConversationService::toSummary)
        .toList();
  }

  /**
   * Messages of a conversation for rendering on resume. Only user turns and assistant turns that
   * produced visible text are returned; intermediate tool-call/tool-result rows are omitted.
   */
  @Transactional(readOnly = true)
  public List<MessageView> messages(final String conversationId) {
    return messageRepository.findByConversationIdOrderBySeqAsc(conversationId).stream()
        .filter(ConversationService::isRenderable)
        .map(
            m ->
                new MessageView(m.getRole(), m.getContent(), JsonListCodec.fromJson(m.getImages())))
        .toList();
  }

  /** Renames a conversation; empty when the id does not exist. */
  @Transactional
  public Optional<ConversationSummary> rename(final String conversationId, final String title) {
    return conversations
        .findById(conversationId)
        .map(
            c -> {
              c.setTitle(title.trim());
              conversations.save(c);
              return toSummary(c);
            });
  }

  /** Deletes a conversation and its messages. Idempotent. */
  @Transactional
  public void delete(final String conversationId) {
    conversations.deleteById(conversationId);
  }

  private static boolean isRenderable(final ChatMessageEntity message) {
    final String role = message.getRole();
    return ROLE_USER.equals(role)
        || (ROLE_ASSISTANT.equals(role)
            && message.getContent() != null
            && !message.getContent().isBlank());
  }

  private static ConversationSummary toSummary(final Conversation c) {
    return new ConversationSummary(
        c.getId(),
        c.getTitle(),
        c.getServer(),
        c.getModel(),
        c.getUpdatedAt(),
        c.getTemperature(),
        c.getNumCtx(),
        JsonListCodec.fromJson(c.getStopSequences()));
  }
}

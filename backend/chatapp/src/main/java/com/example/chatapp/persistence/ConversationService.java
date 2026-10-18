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
  private final com.example.chatapp.agent.AgentRunRepository agentRuns;
  private final ChatMessageRepository messageRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.example.chatapp.memory.CurrentUserResolver users;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.example.chatapp.context.ExecutionCoordinator coordinator;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private org.springframework.transaction.PlatformTransactionManager manager;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  private void owned(String id) {
    if (users != null) users.requireOwnedConversation(id);
  }

  public ConversationService(
      final ConversationRepository conversations,
      final ChatMessageRepository messageRepository,
      final com.example.chatapp.agent.AgentRunRepository agentRuns) {
    this.conversations = conversations;
    this.agentRuns = agentRuns;
    this.messageRepository = messageRepository;
  }

  /** All conversations, most-recently-updated first. */
  @Transactional(readOnly = true)
  public List<ConversationSummary> list() {
    return (users == null
            ? conversations.findAllByOrderByUpdatedAtDesc()
            : conversations.findAllByOwnerIdOrderByUpdatedAtDesc(users.currentUserId()))
        .stream().map(ConversationService::toSummary).toList();
  }

  /**
   * Messages of a conversation for rendering on resume. Only user turns and assistant turns that
   * produced visible text are returned; intermediate tool-call/tool-result rows are omitted.
   */
  @Transactional(readOnly = true)
  public List<MessageView> messages(final String conversationId) {
    owned(conversationId);
    return messageRepository.findByConversationIdOrderBySeqAsc(conversationId).stream()
        .filter(ConversationService::isRenderable)
        .map(
            m ->
                new MessageView(
                    m.getId(), m.getRole(), m.getContent(), JsonListCodec.fromJson(m.getImages())))
        .toList();
  }

  /** Renames a conversation; empty when the id does not exist. */
  public Optional<ConversationSummary> rename(final String conversationId, final String title) {
    if (users == null || coordinator == null || manager == null)
      return renameInternal(conversationId, title);
    return coordinator.withOwner(
        users.currentUserId(),
        () ->
            new org.springframework.transaction.support.TransactionTemplate(manager)
                .execute(status -> renameInternal(conversationId, title)));
  }

  private Optional<ConversationSummary> renameInternal(
      final String conversationId, final String title) {
    if (users != null) users.assertConversationAccessible(conversationId, true);
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
  public void delete(final String conversationId) {
    if (users == null || coordinator == null || manager == null) {
      deleteInternal(conversationId);
      return;
    }
    coordinator.withOwner(
        users.currentUserId(),
        () ->
            new org.springframework.transaction.support.TransactionTemplate(manager)
                .execute(
                    status -> {
                      owned(conversationId);
                      coordinator.assertIdle(users.currentUserId(), conversationId);
                      deleteInternal(conversationId);
                      return null;
                    }));
  }

  private void deleteInternal(final String conversationId) {
    if (agentRuns.existsByConversationIdAndStatusIn(
        conversationId,
        List.of(
            com.example.chatapp.agent.AgentRunStatus.QUEUED,
            com.example.chatapp.agent.AgentRunStatus.RUNNING,
            com.example.chatapp.agent.AgentRunStatus.CANCEL_REQUESTED))) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "Cancel the active agent task before deleting this conversation.");
    }
    if (jdbc != null) {
      jdbc.update(
          "delete from project_memory_source where source_conversation_id=?", conversationId);
      jdbc.update("delete from context_use where conversation_id=?", conversationId);
    }
    agentRuns.deleteAll(agentRuns.findByConversationIdOrderByCreatedAtDesc(conversationId));
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
    ConversationSummary result =
        new ConversationSummary(
            c.getId(),
            c.getTitle(),
            c.getServer(),
            c.getModel(),
            c.getUpdatedAt(),
            c.getTemperature(),
            c.getNumCtx(),
            JsonListCodec.fromJson(c.getStopSequences()));
    result.setContextSettings(
        c.getProjectId(), c.isUsePreferences(), c.isUseProjectContext(), c.getSettingsVersion());
    return result;
  }
}

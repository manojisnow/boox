package com.example.chatapp.persistence;

import com.example.chatapp.engine.ChatContextService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQLite-backed {@link ChatContextService}. Persists conversations and messages so history survives
 * restarts. Marked {@link Primary} so it is preferred over the in-memory implementation.
 */
@Service
@Primary
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Injected Spring Data repositories are singleton beans; safe to retain.")
public class JpaChatContextService implements ChatContextService {

  private static final int TITLE_MAX_LENGTH = 60;

  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;

  public JpaChatContextService(
      final ConversationRepository conversations, final ChatMessageRepository messages) {
    this.conversations = conversations;
    this.messages = messages;
  }

  @Override
  @Transactional
  public List<Map<String, Object>> getContext(final String sessionId) {
    getOrCreate(sessionId);
    final List<Map<String, Object>> context = new ArrayList<>();
    for (final ChatMessageEntity message : messages.findByConversationIdOrderBySeqAsc(sessionId)) {
      final Map<String, Object> entry = new HashMap<>();
      entry.put("role", message.getRole());
      entry.put("content", nullToEmpty(message.getContent()));
      final List<String> images = ImageCodec.fromJson(message.getImages());
      if (!images.isEmpty()) {
        entry.put("images", images);
      }
      context.add(entry);
    }
    return context;
  }

  @Override
  @Transactional
  public void addMessage(
      final String sessionId, final String role, final String content, final List<String> images) {
    final Conversation conversation = getOrCreate(sessionId);
    final Integer maxSeq = messages.findMaxSeq(sessionId);
    final int nextSeq = maxSeq == null ? 0 : maxSeq + 1;
    final ChatMessageEntity entity = new ChatMessageEntity(conversation, nextSeq, role, content);
    entity.setImages(ImageCodec.toJson(images));
    messages.save(entity);
    if ("user".equals(role)
        && Conversation.DEFAULT_TITLE.equals(conversation.getTitle())
        && content != null
        && !content.isBlank()) {
      conversation.setTitle(deriveTitle(content));
    }
    conversation.setUpdatedAt(Instant.now());
    conversations.save(conversation);
  }

  @Override
  @Transactional
  public void setSystemPrompt(final String sessionId, final String systemPrompt) {
    final Conversation conversation = getOrCreate(sessionId);
    if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
      conversation.setSystemPrompt(systemPrompt.trim());
    } else {
      conversation.setSystemPrompt(null);
    }
    conversations.save(conversation);
  }

  @Override
  @Transactional(readOnly = true)
  public String getSystemPrompt(final String sessionId) {
    return conversations.findById(sessionId).map(Conversation::getSystemPrompt).orElse(null);
  }

  @Override
  @Transactional
  public void resetContext(final String sessionId) {
    messages.deleteByConversationId(sessionId);
    conversations
        .findById(sessionId)
        .ifPresent(
            conversation -> {
              conversation.setSystemPrompt(null);
              conversation.setUpdatedAt(Instant.now());
              conversations.save(conversation);
            });
  }

  @Override
  @Transactional
  public void setMetadata(final String sessionId, final String server, final String model) {
    final Conversation conversation = getOrCreate(sessionId);
    if (server != null && !server.isBlank()) {
      conversation.setServer(server);
    }
    if (model != null && !model.isBlank()) {
      conversation.setModel(model);
    }
    conversations.save(conversation);
  }

  @Override
  @Transactional(readOnly = true)
  public String getSummary(final String sessionId) {
    return conversations.findById(sessionId).map(Conversation::getSummary).orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public int getSummarizedCount(final String sessionId) {
    return conversations.findById(sessionId).map(Conversation::getSummarizedCount).orElse(0);
  }

  @Override
  @Transactional
  public void setSummaryState(
      final String sessionId, final String summary, final int summarizedCount) {
    final Conversation conversation = getOrCreate(sessionId);
    conversation.setSummary(summary);
    conversation.setSummarizedCount(summarizedCount);
    conversations.save(conversation);
  }

  private Conversation getOrCreate(final String sessionId) {
    return conversations
        .findById(sessionId)
        .orElseGet(() -> conversations.save(new Conversation(sessionId)));
  }

  private static String deriveTitle(final String firstUserMessage) {
    final String trimmed = firstUserMessage.trim();
    if (trimmed.length() <= TITLE_MAX_LENGTH) {
      return trimmed;
    }
    return trimmed.substring(0, TITLE_MAX_LENGTH).trim() + "…";
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
}

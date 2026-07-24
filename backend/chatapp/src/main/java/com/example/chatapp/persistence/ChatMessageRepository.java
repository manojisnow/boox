package com.example.chatapp.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link ChatMessageEntity} rows. */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

  /** Messages of a conversation in insertion order. */
  List<ChatMessageEntity> findByConversationIdOrderBySeqAsc(String conversationId);

  /** Highest {@code seq} used so far in a conversation, or null if none. */
  @org.springframework.data.jpa.repository.Query(
      "select max(m.seq) from ChatMessageEntity m where m.conversation.id = :conversationId")
  Integer findMaxSeq(String conversationId);

  /** Removes all messages of a conversation (used by resetContext). */
  void deleteByConversationId(String conversationId);
}

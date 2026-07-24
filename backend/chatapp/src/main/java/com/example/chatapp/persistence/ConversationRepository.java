package com.example.chatapp.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Conversation} entities. */
public interface ConversationRepository extends JpaRepository<Conversation, String> {

  /** Conversations ordered most-recently-updated first, for the sidebar. */
  List<Conversation> findAllByOrderByUpdatedAtDesc();
}

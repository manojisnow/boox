package com.example.chatapp.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Conversation} entities. */
public interface ConversationRepository extends JpaRepository<Conversation, String> {

  /** Conversations ordered most-recently-updated first, for the sidebar. */
  List<Conversation> findAllByOrderByUpdatedAtDesc();

  List<Conversation> findAllByOwnerIdOrderByUpdatedAtDesc(String ownerId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "update Conversation c set c.webSessionId = :session where c.id = :id and c.webSessionId is null")
  int initializeWebSession(
      @org.springframework.data.repository.query.Param("id") String id,
      @org.springframework.data.repository.query.Param("session") String session);

  @org.springframework.data.jpa.repository.Query(
      "select c.webSessionId from Conversation c where c.id = :id")
  String findWebSession(@org.springframework.data.repository.query.Param("id") String id);
}

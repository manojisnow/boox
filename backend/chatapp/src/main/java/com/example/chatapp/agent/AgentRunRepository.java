package com.example.chatapp.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for persisted agent runs. */
public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
  List<AgentRun> findByConversationIdOrderByCreatedAtDesc(String conversationId);
}

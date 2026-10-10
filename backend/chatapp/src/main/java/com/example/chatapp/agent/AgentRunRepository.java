package com.example.chatapp.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for persisted agent runs. */
public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
  boolean existsByConversationIdAndStatusIn(String conversationId, List<AgentRunStatus> statuses);

  List<AgentRun> findByStatusIn(List<AgentRunStatus> statuses);

  List<AgentRun> findByConversationIdOrderByCreatedAtDesc(String conversationId);
}

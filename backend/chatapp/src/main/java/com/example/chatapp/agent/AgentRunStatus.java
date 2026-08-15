package com.example.chatapp.agent;

/** Lifecycle states for one bounded agent task. */
public enum AgentRunStatus {
  QUEUED,
  RUNNING,
  WAITING_FOR_APPROVAL,
  CANCEL_REQUESTED,
  COMPLETED,
  FAILED,
  CANCELLED
}

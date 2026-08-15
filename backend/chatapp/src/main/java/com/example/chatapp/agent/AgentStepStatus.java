package com.example.chatapp.agent;

/** Execution status for an individual {@link AgentStep}. */
public enum AgentStepStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED,
  SKIPPED
}

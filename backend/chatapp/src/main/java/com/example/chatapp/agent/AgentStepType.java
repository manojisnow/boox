package com.example.chatapp.agent;

/** The kinds of durable events recorded while an agent works on a task. */
public enum AgentStepType {
  PLAN,
  MODEL,
  TOOL,
  APPROVAL,
  FINAL
}

package com.example.chatapp.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** User-selected text file supplied as evidence for one agent task. */
public class AgentAttachment {
  @NotBlank
  @Size(max = 255)
  private String name;

  @NotNull
  @Size(max = 50_000)
  private String content;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }
}

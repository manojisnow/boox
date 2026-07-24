package com.example.chatapp.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for renaming a conversation. */
public class RenameRequest {

  @NotBlank private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(final String title) {
    this.title = title;
  }
}

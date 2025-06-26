package com.example.chatapp.engine;

@SuppressWarnings("PMD.UnnecessaryConstructor")
public class ModelInfo {
  private final String name;
  private final String description;

  public ModelInfo(final String name, final String description) {
    this.name = name;
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }
}

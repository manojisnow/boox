package com.example.chatapp.engine;

import java.util.List;

public class ModelInfo {
  private final String name;
  private final String description;
  private final List<String> capabilities;

  public ModelInfo(final String name, final String description) {
    this(name, description, List.of());
  }

  public ModelInfo(final String name, final String description, final List<String> capabilities) {
    this.name = name;
    this.description = description;
    this.capabilities = capabilities == null ? List.of() : capabilities;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public List<String> getCapabilities() {
    return capabilities;
  }
}

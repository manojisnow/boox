package com.example.chatapp.engine;

import java.util.List;

/**
 * Per-conversation generation settings that override the server defaults. Any field may be {@code
 * null}/empty, meaning "use the server default" for that setting.
 */
public final class GenerationConfig {

  private final Double temperature;
  private final Integer numCtx;
  private final List<String> stopSequences;

  public GenerationConfig(
      final Double temperature, final Integer numCtx, final List<String> stopSequences) {
    this.temperature = temperature;
    this.numCtx = numCtx;
    this.stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
  }

  /** No overrides — every setting falls back to the server default. */
  public static GenerationConfig empty() {
    return new GenerationConfig(null, null, List.of());
  }

  public Double getTemperature() {
    return temperature;
  }

  public Integer getNumCtx() {
    return numCtx;
  }

  public List<String> getStopSequences() {
    return stopSequences;
  }
}

package com.example.chatapp.context;

import java.util.Map;

/** Raw text exists only for the worker lifetime; metadata never contains saved text. */
public record PersonalizationContext(String text, Map<String, Object> contextUsed) {
  public PersonalizationContext {
    contextUsed = Map.copyOf(contextUsed);
  }

  private static final ThreadLocal<PersonalizationContext> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<Runnable> CHECK = new ThreadLocal<>();

  public static void guard(Runnable check) {
    CHECK.set(check);
  }

  public static void checkpoint() {
    Runnable check = CHECK.get();
    if (check != null) check.run();
  }

  public static PersonalizationContext current() {
    return CURRENT.get();
  }

  public static void install(PersonalizationContext context) {
    CURRENT.set(context);
  }

  public static void clear() {
    CURRENT.remove();
    CHECK.remove();
  }
}

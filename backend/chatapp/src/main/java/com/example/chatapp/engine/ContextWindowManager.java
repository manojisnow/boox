package com.example.chatapp.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Splits a conversation's message list into a recent "window" that fits a token budget and the
 * older "dropped" messages that do not. Pure logic — no Spring or IO — so it is trivially testable.
 *
 * <p>Tokens are estimated as roughly one per four characters, which is accurate enough for
 * budgeting without pulling in a model-specific tokenizer.
 */
public final class ContextWindowManager {

  private static final int CHARS_PER_TOKEN = 4;
  private static final String ROLE = "role";
  private static final String CONTENT = "content";
  private static final String ROLE_USER = "user";

  /** Result of splitting a message list at the context-window boundary. */
  public static final class Split {
    private final List<Map<String, String>> dropped;
    private final List<Map<String, String>> window;

    Split(final List<Map<String, String>> dropped, final List<Map<String, String>> window) {
      this.dropped = dropped;
      this.window = window;
    }

    public List<Map<String, String>> getDropped() {
      return new ArrayList<>(dropped);
    }

    public List<Map<String, String>> getWindow() {
      return new ArrayList<>(window);
    }
  }

  /** Estimates the token cost of a piece of text (~1 token per 4 characters, minimum 1). */
  public int estimateTokens(final String text) {
    if (text == null || text.isEmpty()) {
      return 1;
    }
    return Math.max(1, text.length() / CHARS_PER_TOKEN);
  }

  private int messageTokens(final Map<String, String> message) {
    return estimateTokens(message.get(CONTENT)) + estimateTokens(message.get(ROLE));
  }

  /**
   * Keeps the most recent messages whose combined estimated tokens stay within {@code
   * budgetTokens}, returning them as the window and the rest as dropped. The window is nudged
   * forward so it begins on a {@code user} message, avoiding an orphaned leading {@code
   * tool}/assistant-continuation turn. At least the final message is always kept.
   */
  public Split split(final List<Map<String, String>> messages, final int budgetTokens) {
    if (messages == null || messages.isEmpty()) {
      return new Split(new ArrayList<>(), new ArrayList<>());
    }
    int start = messages.size();
    int used = 0;
    for (int i = messages.size() - 1; i >= 0; i--) {
      final int cost = messageTokens(messages.get(i));
      if (start != messages.size() && used + cost > budgetTokens) {
        break;
      }
      used += cost;
      start = i;
    }
    // Advance to the first user message so the window doesn't start mid-turn.
    while (start < messages.size() - 1 && !ROLE_USER.equals(messages.get(start).get(ROLE))) {
      start++;
    }
    return new Split(
        new ArrayList<>(messages.subList(0, start)),
        new ArrayList<>(messages.subList(start, messages.size())));
  }
}

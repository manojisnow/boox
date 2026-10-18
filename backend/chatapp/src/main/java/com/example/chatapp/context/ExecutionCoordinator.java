package com.example.chatapp.context;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Single-process lifecycle coordination. No model/network operation belongs inside withOwner. */
@Component
public class ExecutionCoordinator {
  private record Execution(
      String token, String conversation, Set<String> references, String state) {}

  private final Map<String, Object> mutexes = new ConcurrentHashMap<>();
  private final Map<String, Execution> active = new ConcurrentHashMap<>();

  public <T> T withOwner(String owner, Supplier<T> work) {
    synchronized (mutexes.computeIfAbsent(owner, ignored -> new Object())) {
      return work.get();
    }
  }

  private String key(String owner, String conversation) {
    return owner + ":" + conversation;
  }

  public void assertIdle(String owner, String conversation) {
    if (active.containsKey(key(owner, conversation)))
      conflict("A task is still active in this conversation. Wait for it to finish.");
  }

  public String reserve(String owner, String conversation) {
    return withOwner(
        owner,
        () -> {
          assertIdle(owner, conversation);
          String token = UUID.randomUUID().toString();
          active.put(
              key(owner, conversation), new Execution(token, conversation, Set.of(), "queued"));
          return token;
        });
  }

  public void start(String owner, String conversation, String token, Set<String> references) {
    withOwner(
        owner,
        () -> {
          check(owner, conversation, token);
          active.put(
              key(owner, conversation),
              new Execution(token, conversation, Set.copyOf(references), "running"));
          return null;
        });
  }

  public void cancellationRequested(String owner, String conversation) {
    withOwner(
        owner,
        () -> {
          Execution current = active.get(key(owner, conversation));
          if (current != null)
            active.put(
                key(owner, conversation),
                new Execution(
                    current.token(), conversation, current.references(), "cancellation_requested"));
          return null;
        });
  }

  public void check(String owner, String conversation, String token) {
    Execution current = active.get(key(owner, conversation));
    if (current == null
        || !current.token().equals(token)
        || "cancellation_requested".equals(current.state()))
      throw new java.util.concurrent.CancellationException(
          "Execution no longer owns this conversation");
  }

  public void finish(String owner, String conversation, String token) {
    withOwner(
        owner,
        () -> {
          Execution current = active.get(key(owner, conversation));
          if (current != null && current.token().equals(token))
            active.remove(key(owner, conversation));
          return null;
        });
  }

  public void finishExecution(String owner, String token) {
    withOwner(
        owner,
        () -> {
          active
              .entrySet()
              .removeIf(
                  entry ->
                      entry.getKey().startsWith(owner + ":")
                          && entry.getValue().token().equals(token));
          return null;
        });
  }

  public void assertNotReferenced(String owner, String reference) {
    assertNotReferenced(owner, Set.of(reference));
  }

  public void assertNotReferenced(String owner, Set<String> references) {
    for (Map.Entry<String, Execution> entry : active.entrySet()) {
      if (entry.getKey().startsWith(owner + ":")
          && entry.getValue().references().stream().anyMatch(references::contains))
        conflict(
            "This context is being used by a running task. Wait for it to finish before clearing it.");
    }
  }

  public Map<String, Object> status(String owner, String conversation) {
    Execution current = active.get(key(owner, conversation));
    return current == null
        ? Map.of("state", "idle", "cancellable", false)
        : Map.of("state", current.state(), "executionId", current.token(), "cancellable", false);
  }

  private void conflict(String message) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}

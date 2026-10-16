package com.example.chatapp.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ExecutionCoordinatorTest {
  @Test
  void workerOwnsReservationUntilCleanup() {
    ExecutionCoordinator coordinator = new ExecutionCoordinator();
    String token = coordinator.reserve("owner", "chat");
    coordinator.start("owner", "chat", token, Set.of("preferences:owner"));
    assertThrows(ResponseStatusException.class, () -> coordinator.reserve("owner", "chat"));
    assertThrows(
        ResponseStatusException.class,
        () -> coordinator.assertNotReferenced("owner", "preferences:owner"));
    coordinator.finish("owner", "chat", "wrong-token");
    assertEquals("running", coordinator.status("owner", "chat").get("state"));
    coordinator.finish("owner", "chat", token);
    assertEquals("idle", coordinator.status("owner", "chat").get("state"));
    assertThrows(CancellationException.class, () -> coordinator.check("owner", "chat", token));
  }

  @Test
  void snapshotRegistrationAndDeletionShareAtomicBoundary() throws Exception {
    ExecutionCoordinator coordinator = new ExecutionCoordinator();
    String token = coordinator.reserve("owner", "chat");
    CountDownLatch read = new CountDownLatch(1), finishRead = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> snapshot =
          workers.submit(
              () ->
                  coordinator.withOwner(
                      "owner",
                      () -> {
                        read.countDown();
                        try {
                          assertTrue(finishRead.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                          throw new AssertionError(e);
                        }
                        coordinator.start("owner", "chat", token, Set.of("memory:note"));
                        return null;
                      }));
      assertTrue(read.await(5, TimeUnit.SECONDS));
      Future<?> deletion =
          workers.submit(
              () ->
                  coordinator.withOwner(
                      "owner",
                      () -> {
                        coordinator.assertNotReferenced("owner", "memory:note");
                        return null;
                      }));
      finishRead.countDown();
      snapshot.get(5, TimeUnit.SECONDS);
      ExecutionException error =
          assertThrows(ExecutionException.class, () -> deletion.get(5, TimeUnit.SECONDS));
      assertInstanceOf(ResponseStatusException.class, error.getCause());
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void cancellationRevokesCallsButRetainsReferencesUntilWorkerFinally() {
    ExecutionCoordinator coordinator = new ExecutionCoordinator();
    String token = coordinator.reserve("owner", "chat");
    coordinator.start("owner", "chat", token, Set.of("project:p"));
    coordinator.cancellationRequested("owner", "chat");
    assertThrows(CancellationException.class, () -> coordinator.check("owner", "chat", token));
    assertThrows(ResponseStatusException.class, () -> coordinator.assertIdle("owner", "chat"));
    assertThrows(
        ResponseStatusException.class, () -> coordinator.assertNotReferenced("owner", "project:p"));
    coordinator.finish("owner", "chat", token);
    assertDoesNotThrow(() -> coordinator.assertIdle("owner", "chat"));
  }
}

package com.example.chatapp.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class OllamaChatEngineIntegrationTest {
  @Container
  static MockServerContainer mockServer =
      new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));

  @Autowired OllamaChatEngine engine;

  @Autowired ChatContextService chatContextService;

  static MockServerClient client;

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    registry.add(
        "ollama.api.url",
        () -> "http://" + mockServer.getHost() + ":" + mockServer.getServerPort());
  }

  @BeforeAll
  static void setupMockServer() {
    client = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
    // /api/tags endpoint
    client
        .when(HttpRequest.request().withMethod("GET").withPath("/api/tags"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{" + "\"models\": [{\"name\": \"mock-model\"}]" + "}"));
    // /api/chat endpoint
    client
        .when(HttpRequest.request().withMethod("POST").withPath("/api/chat"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(
                    "{"
                        + "\"message\": {\"role\": \"assistant\", \"content\": \"Hello from MockServer!\"}"
                        + "}"));
  }

  @AfterAll
  static void stopMockServer() {
    if (client != null) client.close();
  }

  @BeforeEach
  void resetContext() {
    chatContextService.resetContext("test-session");
  }

  @Test
  void getModels_returnsMockedModel() {
    List<ModelInfo> models = engine.getModels();
    assertEquals(1, models.size());
    assertEquals("mock-model", models.get(0).getName());
  }

  @Test
  void sendMessage_returnsMockedResponse() {
    Map<String, String> reply = engine.sendMessage("Hi", "mock-model", "test-session", false);
    assertEquals("assistant", reply.get("role"));
    assertEquals("Hello from MockServer!", reply.get("content"));
  }
}

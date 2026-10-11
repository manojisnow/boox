package com.example.chatapp.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

class WebProviderConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean("toolRestTemplate", RestTemplate.class, RestTemplate::new)
          .withBean(WebSessionService.class, () -> mock(WebSessionService.class))
          .withUserConfiguration(
              WebSearchTool.class,
              ParallelSearchTool.class,
              WebFetchTool.class,
              ParallelWebClient.class);

  @Test
  void defaultsToLegacyWithoutMakingParallelConnections() {
    runner.run(
        context -> {
          assertEquals(1, context.getBeansOfType(Tool.class).size());
          assertNotNull(context.getBean(WebSearchTool.class));
          assertTrue(context.getBeansOfType(ParallelWebClient.class).isEmpty());
        });
  }

  @Test
  void parallelRegistersExactlySearchAndFetchAndCanDisableBoth() {
    runner
        .withPropertyValues("tools.websearch.provider=parallel")
        .run(
            context -> {
              assertEquals(2, context.getBeansOfType(Tool.class).size());
              assertTrue(context.getBeansOfType(WebSearchTool.class).isEmpty());
              assertNotNull(context.getBean(ParallelSearchTool.class));
              assertNotNull(context.getBean(WebFetchTool.class));
            });
    runner
        .withPropertyValues("tools.websearch.provider=parallel", "tools.websearch.enabled=false")
        .run(context -> assertTrue(context.getBeansOfType(Tool.class).isEmpty()));
    runner
        .withPropertyValues("tools.websearch.enabled=false")
        .run(context -> assertTrue(context.getBeansOfType(Tool.class).isEmpty()));
  }
}

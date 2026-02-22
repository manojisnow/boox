package com.example.chatapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Configures the bounded thread pool used for SSE streaming requests. */
@Configuration
public class AsyncConfig {

  private static final int CORE_POOL_SIZE = 4;
  private static final int MAX_POOL_SIZE = 20;
  private static final int QUEUE_CAPACITY = 50;

  /**
   * A bounded executor for SSE streaming. Spring manages its lifecycle and shuts it down cleanly on
   * application stop.
   */
  @Bean(name = "streamTaskExecutor")
  public ThreadPoolTaskExecutor streamTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(CORE_POOL_SIZE);
    executor.setMaxPoolSize(MAX_POOL_SIZE);
    executor.setQueueCapacity(QUEUE_CAPACITY);
    executor.setThreadNamePrefix("sse-stream-");
    executor.initialize();
    return executor;
  }
}

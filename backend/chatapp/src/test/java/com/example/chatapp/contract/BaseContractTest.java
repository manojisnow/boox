package com.example.chatapp.contract;

import com.example.chatapp.controller.ChatController;
import com.example.chatapp.service.ChatService;
import com.example.chatapp.tool.ToolRegistry;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/ullavox-contract-test.db")
public abstract class BaseContractTest {

  @Autowired private WebApplicationContext context;

  @MockitoBean private ChatService chatService;

  @MockitoBean private ToolRegistry toolRegistry;

  @BeforeEach
  public void setup() {
    RestAssuredMockMvc.standaloneSetup(new ChatController(chatService, toolRegistry));
  }
}

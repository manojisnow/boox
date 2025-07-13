package com.example.chatapp.contract;

import com.example.chatapp.controller.ChatController;
import com.example.chatapp.service.ChatService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
public abstract class BaseContractTest {

  @Autowired private WebApplicationContext context;

  @MockBean private ChatService chatService;

  @BeforeEach
  public void setup() {
    RestAssuredMockMvc.standaloneSetup(new ChatController(chatService));
  }
}

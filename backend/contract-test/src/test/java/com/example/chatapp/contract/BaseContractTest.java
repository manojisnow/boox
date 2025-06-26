package com.example.chatapp.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContractTestConfig.class)
public abstract class BaseContractTest {

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  public void setup() {
    RestAssuredMockMvc.mockMvc(mockMvc);
  }
}

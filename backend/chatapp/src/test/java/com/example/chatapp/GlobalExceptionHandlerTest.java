package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;

import com.example.chatapp.controller.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {
  @Test
  void handleValidationExceptions_returnsBadRequest() {
    BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
    bindingResult.addError(new FieldError("obj", "field", "msg"));
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException((MethodParameter) null, bindingResult);
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);
    assertEquals(400, response.getStatusCode().value());
    assertEquals("msg", response.getBody().get("field"));
  }

  @Test
  void handleNoResourceFound_returnsNotFound() {
    NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing.js");
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ResponseEntity<Map<String, String>> response = handler.handleNoResourceFound(ex);
    assertEquals(404, response.getStatusCode().value());
    assertEquals("Resource not found", response.getBody().get("error"));
  }

  @Test
  void handleGenericException_returnsInternalServerError() {
    Exception ex = new Exception("fail");
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ResponseEntity<Map<String, String>> response = handler.handleGenericException(ex);
    assertEquals(500, response.getStatusCode().value());
    assertEquals("fail", response.getBody().get("error"));
  }
}

package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;

import com.example.chatapp.controller.GlobalExceptionHandler;
import java.beans.PropertyEditor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {
  @Test
  void handleValidationExceptions_returnsBadRequest() {
    BindingResult bindingResult =
        new BindingResult() {
          @Override
          public List<FieldError> getFieldErrors() {
            return List.of(new FieldError("obj", "field", "msg"));
          }

          @Override
          public List<FieldError> getFieldErrors(String field) {
            return List.of(new FieldError("obj", "field", "msg"));
          }

          @Override
          public FieldError getFieldError() {
            return new FieldError("obj", "field", "msg");
          }

          @Override
          public FieldError getFieldError(String field) {
            return new FieldError("obj", "field", "msg");
          }

          @Override
          public int getFieldErrorCount() {
            return 1;
          }

          @Override
          public int getFieldErrorCount(String field) {
            return 1;
          }

          @Override
          public boolean hasFieldErrors() {
            return true;
          }

          @Override
          public boolean hasFieldErrors(String field) {
            return true;
          }

          @Override
          public void addError(ObjectError error) {}

          @Override
          public List<ObjectError> getAllErrors() {
            return List.of();
          }

          @Override
          public int getErrorCount() {
            return 1;
          }

          @Override
          public boolean hasErrors() {
            return true;
          }

          @Override
          public String getObjectName() {
            return null;
          }

          @Override
          public void setNestedPath(String nestedPath) {}

          @Override
          public String getNestedPath() {
            return null;
          }

          @Override
          public void pushNestedPath(String subPath) {}

          @Override
          public void popNestedPath() throws IllegalStateException {}

          @Override
          public void reject(String errorCode) {}

          @Override
          public void reject(String errorCode, String defaultMessage) {}

          @Override
          public void reject(String errorCode, Object[] errorArgs, String defaultMessage) {}

          @Override
          public void rejectValue(String field, String errorCode) {}

          @Override
          public void rejectValue(String field, String errorCode, String defaultMessage) {}

          @Override
          public void rejectValue(
              String field, String errorCode, Object[] errorArgs, String defaultMessage) {}

          @Override
          public void addAllErrors(org.springframework.validation.Errors errors) {}

          @Override
          public boolean hasGlobalErrors() {
            return false;
          }

          @Override
          public int getGlobalErrorCount() {
            return 0;
          }

          @Override
          public List<ObjectError> getGlobalErrors() {
            return List.of();
          }

          @Override
          public ObjectError getGlobalError() {
            return null;
          }

          @Override
          public Object getFieldValue(String field) {
            return null;
          }

          @Override
          public Class<?> getFieldType(String field) {
            return null;
          }

          @Override
          public Object getTarget() {
            return null;
          }

          @Override
          public Map<String, Object> getModel() {
            return null;
          }

          @Override
          public Object getRawFieldValue(String field) {
            return null;
          }

          @Override
          public PropertyEditor findEditor(String field, Class<?> valueType) {
            return null;
          }

          @Override
          public PropertyEditorRegistry getPropertyEditorRegistry() {
            return null;
          }

          @Override
          public String[] resolveMessageCodes(String errorCode) {
            return new String[0];
          }

          @Override
          public String[] resolveMessageCodes(String errorCode, String field) {
            return new String[0];
          }
        };
    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);
    assertEquals(400, response.getStatusCodeValue());
    assertEquals("msg", response.getBody().get("field"));
  }

  @Test
  void handleGenericException_returnsInternalServerError() {
    Exception ex = new Exception("fail");
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ResponseEntity<Map<String, String>> response = handler.handleGenericException(ex);
    assertEquals(500, response.getStatusCodeValue());
    assertEquals("fail", response.getBody().get("error"));
  }
}

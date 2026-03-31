package com.shipway.ordertracking.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleWebhookProcessingException_returns400WithBody() {
        ResponseEntity<Map<String, Object>> res = handler
                .handleWebhookProcessingException(new WebhookProcessingException("claimio failed"));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertNotNullMap(res);
        assertEquals(false, res.getBody().get("success"));
        assertEquals("claimio failed", res.getBody().get("error"));
    }

    @Test
    void handleValidationException_returns400WithBody() throws Exception {
        Method m = ValidationHolder.class.getDeclaredMethod("post", ValidationDto.class);
        MethodParameter parameter = new MethodParameter(m, 0);
        ValidationDto dto = new ValidationDto();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(dto, "dto");
        binding.addError(new FieldError("dto", "name", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<Map<String, Object>> res = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertNotNullMap(res);
        assertEquals(false, res.getBody().get("success"));
        assertTrue(res.getBody().get("error").toString().contains("Invalid webhook payload"));
    }

    @Test
    void handleGenericException_noResourceFound_rethrows() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing.css");

        assertThrows(NoResourceFoundException.class, () -> handler.handleGenericException(ex));
    }

    @Test
    void handleGenericException_runtime_returns500() throws Exception {
        ResponseEntity<Map<String, Object>> res = handler.handleGenericException(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertNotNullMap(res);
        assertEquals(false, res.getBody().get("success"));
        assertTrue(res.getBody().get("error").toString().contains("Internal server error"));
        assertTrue(res.getBody().get("error").toString().contains("boom"));
    }

    private static void assertNotNullMap(ResponseEntity<Map<String, Object>> res) {
        assertTrue(res.hasBody());
        assertFalse(res.getBody().isEmpty());
    }

    @SuppressWarnings("unused")
    private static final class ValidationHolder {
        void post(ValidationDto dto) {
            // signature only — used for MethodParameter
        }
    }

    private static final class ValidationDto {
        @SuppressWarnings("unused")
        private String name;
    }
}

package com.shipway.ordertracking.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WebhookProcessingExceptionTest {

    @Test
    void messageOnly() {
        WebhookProcessingException ex = new WebhookProcessingException("bad");
        assertEquals("bad", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void messageAndCause() {
        Throwable cause = new IllegalStateException("root");
        WebhookProcessingException ex = new WebhookProcessingException("wrap", cause);
        assertEquals("wrap", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}

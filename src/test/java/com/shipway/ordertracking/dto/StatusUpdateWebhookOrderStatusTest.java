package com.shipway.ordertracking.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link StatusUpdateWebhook.OrderStatus#resolveBrandName()} (brand key for Shopify/Botspace).
 */
class StatusUpdateWebhookOrderStatusTest {

    @Test
    void resolveBrandName_null_returnsNull() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName(null);
        assertNull(o.resolveBrandName());
    }

    @Test
    void resolveBrandName_blank_returnsNull() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("   ");
        assertNull(o.resolveBrandName());
    }

    @Test
    void resolveBrandName_strikerStoreAlias_returnsStri() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("Striker Store");
        assertEquals("STRI", o.resolveBrandName());
    }

    @Test
    void resolveBrandName_dribbleStoreAlias_returnsDrib() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("Dribble Store");
        assertEquals("DRIB", o.resolveBrandName());
    }

    @Test
    void resolveBrandName_configKey_returnsTrimmed() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("  STRI  ");
        assertEquals("STRI", o.resolveBrandName());
    }

    @Test
    void resolveBrandName_otherValue_returnsTrimmed() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("  CUSTOM  ");
        assertEquals("CUSTOM", o.resolveBrandName());
    }
}

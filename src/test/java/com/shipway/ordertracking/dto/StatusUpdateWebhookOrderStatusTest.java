package com.shipway.ordertracking.dto;

import com.shipway.ordertracking.util.BrandAccountKey;
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
    void resolveBrandName_strikerStoreAlias_returnsStrikerStoreKey() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("Striker Store");
        assertEquals(BrandAccountKey.STRIKER_STORE, o.resolveBrandName());
    }

    @Test
    void resolveBrandName_dribbleStoreAlias_returnsDribbleStoreKey() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("Dribble Store");
        assertEquals(BrandAccountKey.DRIBBLE_STORE, o.resolveBrandName());
    }

    @Test
    void resolveBrandName_legacySpacedConfigKeys_mapToCanonical() {
        StatusUpdateWebhook.OrderStatus striker = new StatusUpdateWebhook.OrderStatus();
        striker.setBrandName("STRIKER STORE");
        assertEquals(BrandAccountKey.STRIKER_STORE, striker.resolveBrandName());
        StatusUpdateWebhook.OrderStatus dribble = new StatusUpdateWebhook.OrderStatus();
        dribble.setBrandName("DRIBBLE STORE");
        assertEquals(BrandAccountKey.DRIBBLE_STORE, dribble.resolveBrandName());
    }

    @Test
    void resolveBrandName_configKeyPassthrough_returnsTrimmed() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("  " + BrandAccountKey.STRIKER_STORE + "  ");
        assertEquals(BrandAccountKey.STRIKER_STORE, o.resolveBrandName());
    }

    @Test
    void resolveBrandName_otherValue_returnsTrimmed() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setBrandName("  CUSTOM  ");
        assertEquals("CUSTOM", o.resolveBrandName());
    }
}

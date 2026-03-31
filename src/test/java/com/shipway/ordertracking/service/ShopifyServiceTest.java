package com.shipway.ordertracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipway.ordertracking.config.ShopifyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class ShopifyServiceTest {

    @Mock
    private ShopifyProperties shopifyProperties;

    @Mock
    private org.springframework.web.client.RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShopifyService shopifyService;

    @Test
    void parseNumericIdFromGid_validOrder() {
        Long id = ShopifyService.parseNumericIdFromGid("gid://shopify/Order/6973973135644", "gid://shopify/Order/");
        assertEquals(6973973135644L, id);
    }

    @Test
    void parseNumericIdFromGid_validFulfillment() {
        Long id = ShopifyService.parseNumericIdFromGid("gid://shopify/Fulfillment/12345", "gid://shopify/Fulfillment/");
        assertEquals(12345L, id);
    }

    @Test
    void parseNumericIdFromGid_nullOrWrongPrefix() {
        assertNull(ShopifyService.parseNumericIdFromGid(null, "gid://shopify/Order/"));
        assertNull(ShopifyService.parseNumericIdFromGid("gid://shopify/Order/1", "gid://shopify/Fulfillment/"));
        assertNull(ShopifyService.parseNumericIdFromGid("not-a-gid", "gid://shopify/Order/"));
    }

    @Test
    void parseNumericIdFromGid_invalidNumber() {
        assertNull(ShopifyService.parseNumericIdFromGid("gid://shopify/Order/abc", "gid://shopify/Order/"));
    }

    @Test
    void normalizeShopifyOrderNameKey_stripsHashAndTrims() {
        assertEquals("253243", ShopifyService.normalizeShopifyOrderNameKey("  #253243  "));
        assertEquals("253243", ShopifyService.normalizeShopifyOrderNameKey("#253243"));
        assertEquals("", ShopifyService.normalizeShopifyOrderNameKey(null));
        assertEquals("", ShopifyService.normalizeShopifyOrderNameKey("   "));
    }

    @Test
    void getOpenFulfillmentOrderIdFromEdges_returnsLastOpenFromEnd() {
        Map<String, Object> nodeClosed = Map.of("id", "gid://shopify/FulfillmentOrder/1", "status", "CLOSED");
        Map<String, Object> nodeOpenFirst = Map.of("id", "gid://shopify/FulfillmentOrder/2", "status", "OPEN");
        Map<String, Object> nodeOpenLast = Map.of("id", "gid://shopify/FulfillmentOrder/3", "status", "OPEN");
        Map<String, Object> edge1 = Map.of("node", nodeClosed);
        Map<String, Object> edge2 = Map.of("node", nodeOpenFirst);
        Map<String, Object> edge3 = Map.of("node", nodeOpenLast);
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("fulfillmentOrders", Map.of("edges", List.of(edge1, edge2, edge3)));

        String id = shopifyService.getOpenFulfillmentOrderIdFromEdges(orderData);
        assertEquals("gid://shopify/FulfillmentOrder/3", id);
    }

    @Test
    void getOpenFulfillmentOrderIdFromEdges_noOpen_returnsNull() {
        Map<String, Object> orderData = Map.of(
                "fulfillmentOrders",
                Map.of("edges", List.of(Map.of("node", Map.of("id", "gid://shopify/FulfillmentOrder/1", "status", "CLOSED")))));
        assertNull(shopifyService.getOpenFulfillmentOrderIdFromEdges(orderData));
    }

    @Test
    void getOpenFulfillmentOrderIdFromEdges_nullOrInvalid_returnsNull() {
        assertNull(shopifyService.getOpenFulfillmentOrderIdFromEdges(null));
        assertNull(shopifyService.getOpenFulfillmentOrderIdFromEdges(Map.of()));
    }
}

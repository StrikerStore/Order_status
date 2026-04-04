package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import com.shipway.ordertracking.util.BrandAccountKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopifyFulfillmentFlowServiceTest {

    @Mock
    private ShopifyService shopifyService;

    @Mock
    private ShopifyProperties shopifyProperties;

    @InjectMocks
    private ShopifyFulfillmentFlowService service;

    private StatusUpdateWebhook.OrderStatus baseOrder() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setOrderId("254120");
        o.setBrandName(BrandAccountKey.STRIKER_STORE);
        o.setAwb("AWB123");
        return o;
    }

    /** Used when the flow calls {@code buildTrackingUrl} (unfulfilled path with AWB). */
    private void stubStriTrackingTemplate() {
        ShopifyAccount acc = new ShopifyAccount();
        acc.setTrackingUrlTemplate("https://track.example/t/{awb}");
        when(shopifyProperties.getAccountByCode(BrandAccountKey.STRIKER_STORE)).thenReturn(acc);
    }

    @Test
    void processShopifyFulfillment_missingBrandName_returnsFalse() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setBrandName("");
        assertFalse(service.processShopifyFulfillment(o));
    }

    @Test
    void processShopifyFulfillment_cloneOrder_returnsFalse() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setOrderId("254120_CLONE");
        assertFalse(service.processShopifyFulfillment(o));
    }

    @Test
    void processShopifyFulfillment_orderNotFound_returnsFalse() {
        when(shopifyService.getOrderWithDisplayFulfillmentStatus(anyString(), anyString())).thenReturn(null);
        assertFalse(service.processShopifyFulfillment(baseOrder()));
    }

    @Test
    void processShopifyFulfillment_unfulfilled_createsFulfillmentAndUpdatesTracking() {
        stubStriTrackingTemplate();
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        Map<String, Object> orderNode = new HashMap<>();
        orderNode.put("id", "gid://shopify/Order/777");
        orderNode.put("displayFulfillmentStatus", "UNFULFILLED");

        Map<String, Object> foData = Map.of();
        when(shopifyService.getOrderWithDisplayFulfillmentStatus(BrandAccountKey.STRIKER_STORE, "254120")).thenReturn(orderNode);
        when(shopifyService.getFulfillmentOrdersForOrder(BrandAccountKey.STRIKER_STORE, "gid://shopify/Order/777")).thenReturn(foData);
        when(shopifyService.getOpenFulfillmentOrderIdFromEdges(foData)).thenReturn("gid://shopify/FulfillmentOrder/9");
        when(shopifyService.createFulfillment(eq(BrandAccountKey.STRIKER_STORE), eq(777L), eq("gid://shopify/FulfillmentOrder/9"), eq("AWB123"),
                anyString())).thenReturn(42L);
        when(shopifyService.updateFulfillmentTracking(eq(BrandAccountKey.STRIKER_STORE), eq(777L), eq(42L), eq("AWB123"), eq("LABEL PRINTED")))
                .thenReturn(true);

        assertTrue(service.processShopifyFulfillment(o));
    }

    @Test
    void processShopifyFulfillment_unfulfilled_createNull_fallsBackToExistingFulfillmentId() {
        stubStriTrackingTemplate();
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        Map<String, Object> orderNode = new HashMap<>();
        orderNode.put("id", "gid://shopify/Order/777");
        orderNode.put("displayFulfillmentStatus", "PARTIAL");

        Map<String, Object> foData = Map.of();
        when(shopifyService.getOrderWithDisplayFulfillmentStatus(BrandAccountKey.STRIKER_STORE, "254120")).thenReturn(orderNode);
        when(shopifyService.getFulfillmentOrdersForOrder(BrandAccountKey.STRIKER_STORE, "gid://shopify/Order/777")).thenReturn(foData);
        when(shopifyService.getOpenFulfillmentOrderIdFromEdges(foData)).thenReturn("gid://shopify/FulfillmentOrder/9");
        when(shopifyService.createFulfillment(anyString(), anyLong(), anyString(), any(), any())).thenReturn(null);
        when(shopifyService.getFulfillmentId(BrandAccountKey.STRIKER_STORE, 777L)).thenReturn(99L);
        when(shopifyService.updateFulfillmentTracking(eq(BrandAccountKey.STRIKER_STORE), eq(777L), eq(99L), eq("AWB123"), eq("LABEL PRINTED")))
                .thenReturn(true);

        assertTrue(service.processShopifyFulfillment(o));
    }

    @Test
    void processShopifyFulfillment_alreadyFulfilled_updatesTracking() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        Map<String, Object> fulfillment = new HashMap<>();
        fulfillment.put("id", "gid://shopify/Fulfillment/555");
        List<Map<String, Object>> fulfillments = new ArrayList<>();
        fulfillments.add(fulfillment);

        Map<String, Object> orderNode = new HashMap<>();
        orderNode.put("id", "gid://shopify/Order/777");
        orderNode.put("displayFulfillmentStatus", "FULFILLED");
        orderNode.put("fulfillments", fulfillments);

        when(shopifyService.getOrderWithDisplayFulfillmentStatus(BrandAccountKey.STRIKER_STORE, "254120")).thenReturn(orderNode);
        when(shopifyService.updateFulfillmentTracking(eq(BrandAccountKey.STRIKER_STORE), eq(777L), eq(555L), eq("AWB123"), eq("LABEL PRINTED")))
                .thenReturn(true);

        assertTrue(service.processShopifyFulfillment(o));
    }

    @Test
    void processShopifyFulfillment_noOpenFulfillmentOrder_returnsFalse() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        Map<String, Object> orderNode = new HashMap<>();
        orderNode.put("id", "gid://shopify/Order/777");
        orderNode.put("displayFulfillmentStatus", "UNFULFILLED");

        Map<String, Object> foData = Map.of();
        when(shopifyService.getOrderWithDisplayFulfillmentStatus(BrandAccountKey.STRIKER_STORE, "254120")).thenReturn(orderNode);
        when(shopifyService.getFulfillmentOrdersForOrder(BrandAccountKey.STRIKER_STORE, "gid://shopify/Order/777")).thenReturn(foData);
        when(shopifyService.getOpenFulfillmentOrderIdFromEdges(foData)).thenReturn(null);

        assertFalse(service.processShopifyFulfillment(o));
    }
}

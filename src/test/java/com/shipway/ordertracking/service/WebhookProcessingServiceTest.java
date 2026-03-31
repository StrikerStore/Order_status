package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookProcessingServiceTest {

    @Mock
    private OrderCreatedFlowService orderCreatedFlowService;

    @Mock
    private ShopifyFulfillmentFlowService shopifyFulfillmentFlowService;

    @Mock
    private InTransitFlowService inTransitFlowService;

    @Mock
    private OutForDeliveryFlowService outForDeliveryFlowService;

    @Mock
    private DeliveredFlowService deliveredFlowService;

    @InjectMocks
    private WebhookProcessingService service;

    private static StatusUpdateWebhook.OrderStatus order(String orderId, String phone, String status) {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setOrderId(orderId);
        o.setShippingPhone(phone);
        o.setBrandName("STRI");
        o.setCurrentShipmentStatus(status);
        return o;
    }

    @Test
    void processStatusUpdate_nullOrder_returnsInvalid() {
        Map<String, Object> r = service.processStatusUpdate(null);
        assertFalse((Boolean) r.get("success"));
        assertEquals("Invalid order: null", r.get("message"));
    }

    @Test
    void route_skipsWhenPhoneMissing() {
        StatusUpdateWebhook.OrderStatus o = order("1001", null, "SHIPPED");
        Map<String, Object> r = service.processStatusUpdate(o);
        assertFalse((Boolean) r.get("success"));
        assertEquals("1001", r.get("orderId"));
        verify(shopifyFulfillmentFlowService, never()).processShopifyFulfillment(any());
    }

    @Test
    void route_skipsWhenPhoneBlank() {
        StatusUpdateWebhook.OrderStatus o = order("1001", "", "SHIPPED");
        assertFalse((Boolean) service.processStatusUpdate(o).get("success"));
        verify(shopifyFulfillmentFlowService, never()).processShopifyFulfillment(any());
    }

    @Test
    void route_outForDelivery() {
        when(outForDeliveryFlowService.processOutForDelivery(any())).thenReturn(true);
        StatusUpdateWebhook.OrderStatus o = order("1001", "+15550001", "Out for Delivery");
        assertTrue((Boolean) service.processStatusUpdate(o).get("success"));
        verify(outForDeliveryFlowService).processOutForDelivery(o);
        verify(deliveredFlowService, never()).processDelivered(any());
    }

    @Test
    void route_delivered_exactMatch() {
        when(deliveredFlowService.processDelivered(any())).thenReturn(true);
        StatusUpdateWebhook.OrderStatus o = order("1002", "+15550001", "DELIVERED");
        assertTrue((Boolean) service.processStatusUpdate(o).get("success"));
        verify(deliveredFlowService).processDelivered(o);
    }

    @Test
    void route_inTransit_normalizesUnderscores() {
        when(inTransitFlowService.processInTransit(any())).thenReturn(true);
        StatusUpdateWebhook.OrderStatus o = order("1003", "+15550001", "IN_TRANSIT");
        assertTrue((Boolean) service.processStatusUpdate(o).get("success"));
        verify(inTransitFlowService).processInTransit(o);
    }

    @Test
    void route_pickedUp_goesToInTransit() {
        when(inTransitFlowService.processInTransit(any())).thenReturn(true);
        StatusUpdateWebhook.OrderStatus o = order("1004", "+15550001", "PICKED_UP");
        assertTrue((Boolean) service.processStatusUpdate(o).get("success"));
        verify(inTransitFlowService).processInTransit(o);
    }

    @Test
    void route_shopifyFulfillment_shipped() {
        when(shopifyFulfillmentFlowService.processShopifyFulfillment(any())).thenReturn(true);
        StatusUpdateWebhook.OrderStatus o = order("1005", "+15550001", "SHIPPED");
        assertTrue((Boolean) service.processStatusUpdate(o).get("success"));
        verify(shopifyFulfillmentFlowService).processShopifyFulfillment(o);
    }

    @Test
    void route_shopifyFulfillment_labelGenerated() {
        when(shopifyFulfillmentFlowService.processShopifyFulfillment(any())).thenReturn(true);
        StatusUpdateWebhook.OrderStatus o = order("1006", "+15550001", "LABEL_GENERATED");
        assertTrue((Boolean) service.processStatusUpdate(o).get("success"));
        verify(shopifyFulfillmentFlowService).processShopifyFulfillment(o);
    }

    @Test
    void route_shopifyFulfillment_pickupGenerated() {
        when(shopifyFulfillmentFlowService.processShopifyFulfillment(any())).thenReturn(true);
        StatusUpdateWebhook.OrderStatus o = order("1007", "+15550001", "PICKUP_GENERATED");
        assertTrue((Boolean) service.processStatusUpdate(o).get("success"));
        verify(shopifyFulfillmentFlowService).processShopifyFulfillment(o);
    }

    @Test
    void route_rto_returnsFalseWithoutCallingFlows() {
        StatusUpdateWebhook.OrderStatus o = order("1008", "+15550001", "RTO");
        assertFalse((Boolean) service.processStatusUpdate(o).get("success"));
        verify(inTransitFlowService, never()).processInTransit(any());
        verify(shopifyFulfillmentFlowService, never()).processShopifyFulfillment(any());
    }

    @Test
    void route_unknownStatus_returnsFalse() {
        StatusUpdateWebhook.OrderStatus o = order("1009", "+15550001", "MYSTERY_STATUS");
        assertFalse((Boolean) service.processStatusUpdate(o).get("success"));
        verify(shopifyFulfillmentFlowService, never()).processShopifyFulfillment(any());
    }

    @Test
    void processStatusUpdate_propagatesExceptionAsFailure() {
        when(outForDeliveryFlowService.processOutForDelivery(any())).thenThrow(new RuntimeException("boom"));
        StatusUpdateWebhook.OrderStatus o = order("1010", "+15550001", "OUT FOR DELIVERY");
        Map<String, Object> r = service.processStatusUpdate(o);
        assertFalse((Boolean) r.get("success"));
        assertEquals("Error processing order: boom", r.get("message"));
    }
}

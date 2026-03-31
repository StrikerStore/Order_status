package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.dto.StatusUpdateWebhook;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InTransitFlowServiceTest {

    @Mock
    private BotspaceService botspaceService;

    @Mock
    private ShopifyService shopifyService;

    @Mock
    private BotspaceProperties botspaceProperties;

    @Mock
    private ShopifyProperties shopifyProperties;

    @Mock
    private CustomerMessageTrackingService customerMessageTrackingService;

    @InjectMocks
    private InTransitFlowService service;

    private StatusUpdateWebhook.OrderStatus baseOrder() {
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setOrderId("254120");
        o.setBrandName("STRI");
        o.setShippingPhone("+919876543210");
        o.setCurrentShipmentStatus("IN_TRANSIT");
        o.setPreviousStatus("PICKED_UP");
        return o;
    }

    @Test
    void processInTransit_statusUnchanged_returnsTrue() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setCurrentShipmentStatus("IN_TRANSIT");
        o.setPreviousStatus("IN_TRANSIT");
        assertTrue(service.processInTransit(o));
    }

    @Test
    void processInTransit_missingPhone_returnsFalse() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setShippingPhone(null);
        assertFalse(service.processInTransit(o));
    }

    @Test
    void processInTransit_missingBrandName_returnsFalse() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setBrandName("");
        assertFalse(service.processInTransit(o));
    }

    @Test
    void processInTransit_alreadySentInDb_returnsTrue() {
        when(customerMessageTrackingService.hasAnyStatus(eq("254120"), eq("STRI"), anyList())).thenReturn(true);
        assertTrue(service.processInTransit(baseOrder()));
    }

    @Test
    void processInTransit_cloneOrder_sendsBotspaceOnly() {
        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setOrderId("254120_CLONE");

        when(customerMessageTrackingService.hasAnyStatus(anyString(), anyString(), anyList())).thenReturn(false);

        BotspaceAccount ba = new BotspaceAccount();
        ba.setInTransitTemplateId("tpl_in");
        when(botspaceProperties.getAccountByCode("STRI")).thenReturn(ba);

        ShopifyAccount sa = new ShopifyAccount();
        sa.setTrackingUrlTemplate("https://t/{awb}");
        when(shopifyProperties.getAccountByCode("STRI")).thenReturn(sa);
        o.setAwb("AWB1");

        when(botspaceService.sendTemplateMessage(eq("STRI"), any(), eq("254120_CLONE"), eq("sent_inTransit"),
                eq("failed_inTransit"))).thenReturn(true);

        assertTrue(service.processInTransit(o));
    }

    @Test
    void processInTransit_orderNotFound_returnsFalse() {
        when(customerMessageTrackingService.hasAnyStatus(anyString(), anyString(), anyList())).thenReturn(false);
        when(shopifyService.getOrderWithDisplayFulfillmentStatus(anyString(), anyString())).thenReturn(null);
        assertFalse(service.processInTransit(baseOrder()));
    }

    @Test
    void processInTransit_fulfilled_updatesTrackingAndSendsBotspace() {
        when(customerMessageTrackingService.hasAnyStatus(anyString(), anyString(), anyList())).thenReturn(false);

        Map<String, Object> orderNode = new HashMap<>();
        orderNode.put("id", "gid://shopify/Order/254120");
        orderNode.put("displayFulfillmentStatus", "FULFILLED");
        List<Map<String, Object>> fl = new ArrayList<>();
        fl.add(Map.of("id", "gid://shopify/Fulfillment/555"));
        orderNode.put("fulfillments", fl);

        when(shopifyService.getOrderWithDisplayFulfillmentStatus("STRI", "254120")).thenReturn(orderNode);
        when(shopifyService.updateFulfillmentTracking(eq("STRI"), eq(254120L), eq(555L), any(), eq("in_transit")))
                .thenReturn(true);

        BotspaceAccount ba = new BotspaceAccount();
        ba.setInTransitTemplateId("tpl_in");
        when(botspaceProperties.getAccountByCode("STRI")).thenReturn(ba);

        ShopifyAccount sa = new ShopifyAccount();
        sa.setTrackingUrlTemplate("https://t/{awb}");
        when(shopifyProperties.getAccountByCode("STRI")).thenReturn(sa);

        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setAwb("AWB1");
        when(botspaceService.sendTemplateMessage(eq("STRI"), any(), eq("254120"), eq("sent_inTransit"),
                eq("failed_inTransit"))).thenReturn(true);

        assertTrue(service.processInTransit(o));
    }

    @Test
    void processInTransit_unfulfilled_createsFulfillmentAndSendsBotspace() {
        when(customerMessageTrackingService.hasAnyStatus(anyString(), anyString(), anyList())).thenReturn(false);

        Map<String, Object> orderNode = new HashMap<>();
        orderNode.put("id", "gid://shopify/Order/254120");
        orderNode.put("displayFulfillmentStatus", "UNFULFILLED");
        when(shopifyService.getOrderWithDisplayFulfillmentStatus("STRI", "254120")).thenReturn(orderNode);

        Map<String, Object> foData = Map.of();
        when(shopifyService.getFulfillmentOrdersForOrder("STRI", "gid://shopify/Order/254120")).thenReturn(foData);
        when(shopifyService.getOpenFulfillmentOrderIdFromEdges(foData)).thenReturn("gid://shopify/FulfillmentOrder/9");
        when(shopifyService.createFulfillment(eq("STRI"), eq(254120L), eq("gid://shopify/FulfillmentOrder/9"), any(),
                any())).thenReturn(42L);
        when(shopifyService.updateFulfillmentTracking(eq("STRI"), eq(254120L), eq(42L), any(), eq("in_transit")))
                .thenReturn(true);

        BotspaceAccount ba = new BotspaceAccount();
        ba.setInTransitTemplateId("tpl_in");
        when(botspaceProperties.getAccountByCode("STRI")).thenReturn(ba);
        ShopifyAccount sa = new ShopifyAccount();
        sa.setTrackingUrlTemplate("https://t/{awb}");
        when(shopifyProperties.getAccountByCode("STRI")).thenReturn(sa);

        StatusUpdateWebhook.OrderStatus o = baseOrder();
        o.setAwb("AWB1");
        when(botspaceService.sendTemplateMessage(eq("STRI"), any(), eq("254120"), eq("sent_inTransit"),
                eq("failed_inTransit"))).thenReturn(true);

        assertTrue(service.processInTransit(o));
    }
}

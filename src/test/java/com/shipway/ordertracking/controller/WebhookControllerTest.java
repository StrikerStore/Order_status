package com.shipway.ordertracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import com.shipway.ordertracking.dto.WebhookWrapper;
import com.shipway.ordertracking.service.AbandonedCartFlowService;
import com.shipway.ordertracking.service.OrderCreatedFlowService;
import com.shipway.ordertracking.service.WebhookProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookProcessingService webhookProcessingService;

    @MockBean
    private OrderCreatedFlowService orderCreatedFlowService;

    @MockBean
    private AbandonedCartFlowService abandonedCartFlowService;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/webhook/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("order-tracking-service"));
    }

    @Test
    void statusUpdate_emptyOrders_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orders\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void statusUpdate_singleOrder_delegatesAndReturnsLastResult() throws Exception {
        Map<String, Object> last = Map.of("success", true, "message", "Order processed successfully", "orderId",
                "ORD-1");
        when(webhookProcessingService.processStatusUpdate(any())).thenReturn(last);

        WebhookWrapper wrapper = new WebhookWrapper();
        StatusUpdateWebhook.OrderStatus o = new StatusUpdateWebhook.OrderStatus();
        o.setOrderId("ORD-1");
        o.setShippingPhone("+15550001");
        o.setCurrentShipmentStatus("SHIPPED");
        wrapper.setOrders(List.of(o));

        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrapper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orderId").value("ORD-1"));

        verify(webhookProcessingService).processStatusUpdate(any());
    }

    @Test
    void shopifyOrderCreated_success() throws Exception {
        when(orderCreatedFlowService.processShopifyOrderCreated(any(), anyString())).thenReturn(true);

        mockMvc.perform(post("/webhook/shopify/order-created")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Shopify-Shop-Domain", "shop.myshopify.com")
                .content("{\"id\":1,\"name\":\"#1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void cartAbandoned_invalidJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/webhook/cart-abandoned")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void cartAbandoned_blankBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/webhook/cart-abandoned")
                .contentType(MediaType.APPLICATION_JSON)
                .content("   "))
                .andExpect(status().isBadRequest());
    }
}

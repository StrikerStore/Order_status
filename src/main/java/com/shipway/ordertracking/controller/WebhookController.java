package com.shipway.ordertracking.controller;

import com.shipway.ordertracking.dto.FasterrAbandonedCartWebhook;
import com.shipway.ordertracking.dto.ShopifyOrderCreatedWebhook;
import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import com.shipway.ordertracking.dto.WebhookWrapper;
import com.shipway.ordertracking.service.AbandonedCartFlowService;
import com.shipway.ordertracking.service.OrderCreatedFlowService;
import com.shipway.ordertracking.service.WebhookProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    @Autowired
    private WebhookProcessingService webhookProcessingService;

    @Autowired
    private OrderCreatedFlowService orderCreatedFlowService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Main webhook endpoint that receives status updates
     * Matches the webhook URL pattern: /webhook/claimio_status_hook
     */
    @PostMapping(""
            + ""
            + ""
            + "")
    public ResponseEntity<Map<String, Object>> handleStatusUpdate(@RequestBody WebhookWrapper wrapper) {
        log.info("Received status update webhook");

        if (wrapper == null || wrapper.getOrders() == null || wrapper.getOrders().isEmpty()) {
            log.warn("Received empty webhook orders");
            return createErrorResponse("Empty webhook payload");
        }

        List<StatusUpdateWebhook> webhooks = wrapper.getOrders();
        log.info("Processing {} webhook(s)", webhooks.size());

        // Process the first webhook (based on your sample, it's an array with one
        // object)
        StatusUpdateWebhook webhook = webhooks.get(0);

        try {
            Map<String, Object> result = webhookProcessingService.processStatusUpdate(webhook);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error processing status update webhook", e);
            return createErrorResponse("Error processing webhook: " + e.getMessage());
        }
    }

    /**
     * Shopify order created webhook endpoint
     * Configure this URL in Shopify webhook settings:
     * /webhook/shopify/order-created
     * Shopify will send webhook when order is created
     */
    @PostMapping("/shopify/order-created")
    public ResponseEntity<Map<String, Object>> handleShopifyOrderCreated(
            @RequestBody ShopifyOrderCreatedWebhook webhook,
            @RequestHeader(value = "X-Shopify-Shop-Domain", required = false) String shopDomain) {
        log.info("Received Shopify order created webhook for order: {}", webhook.getName());

        if (webhook == null) {
            log.warn("Received null Shopify webhook");
            return createErrorResponse("Empty webhook payload");
        }

        try {
            // DEBUG MODE: Print request and skip service call
            String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(webhook);
            log.info("🔍 DEBUG: Shopify Order Created Webhook Payload:\n{}", jsonRequest);
            log.info("⚠️ DEBUG MODE: Skipping service call for Order Created Flow");

            // boolean success = orderCreatedFlowService.processShopifyOrderCreated(webhook,
            // shopDomain);
            boolean success = true; // Mock success

            Map<String, Object> response = Map.of(
                    "success", success,
                    "message", success ? "Order created webhook received (DEBUG MODE)"
                            : "Failed to process order created webhook");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing Shopify order created webhook", e);
            return createErrorResponse("Error processing webhook: " + e.getMessage());
        }
    }

    /**
     * Fasterr abandoned cart webhook endpoint
     * Configure this URL in Fasterr webhook settings: /webhook/cart-abandoned
     * Fasterr will send webhook when cart is abandoned
     */
    @PostMapping("/cart-abandoned")
    public ResponseEntity<Map<String, Object>> handleFasterrAbandonedCart(
            @RequestBody List<FasterrAbandonedCartWebhook> webhooks) {
        log.info("Received Fasterr abandoned cart webhook with {} webhook(s)", webhooks != null ? webhooks.size() : 0);

        if (webhooks == null || webhooks.isEmpty()) {
            log.warn("Received empty abandoned cart webhook array");
            return createErrorResponse("Empty webhook payload");
        }

        // Process the first webhook (based on sample, it's an array with one object)
        FasterrAbandonedCartWebhook webhook = webhooks.get(0);

        try {
            // DEBUG MODE: Print request and skip service call
            String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(webhooks);
            log.info("🔍 DEBUG: Fasterr Abandoned Cart Webhook Payload:\n{}", jsonRequest);
            log.info("⚠️ DEBUG MODE: Skipping service call for Abandoned Cart Flow");

            // boolean scheduled = abandonedCartFlowService.processAbandonedCart(webhook);
            boolean scheduled = true; // Mock success

            Map<String, Object> response = Map.of(
                    "success", scheduled,
                    "message", scheduled ? "Abandoned cart notification received (DEBUG MODE)"
                            : "Failed to schedule abandoned cart notification");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing Fasterr abandoned cart webhook", e);
            return createErrorResponse("Error processing webhook: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = Map.of("status", "ok", "service", "order-tracking-service");
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String errorMessage) {
        Map<String, Object> response = Map.of(
                "success", false,
                "error", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}

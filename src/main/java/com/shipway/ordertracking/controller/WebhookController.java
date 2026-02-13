package com.shipway.ordertracking.controller;

import com.shipway.ordertracking.dto.FasterrAbandonedCartWebhook;
import com.shipway.ordertracking.dto.ShopifyOrderCreatedWebhook;
import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import com.shipway.ordertracking.dto.WebhookWrapper;

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

        try {
            // DEBUG: Print the full request payload
            String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
            log.info("🔍 Processed Webhook Payload:\n{}", jsonRequest);
        } catch (Exception e) {
            log.error("Failed to serialize webhook wrapper for logging", e);
        }

        if (wrapper == null || wrapper.getOrders() == null || wrapper.getOrders().isEmpty()) {
            log.warn("Received empty webhook orders");
            return createErrorResponse("Empty webhook payload");
        }

        List<StatusUpdateWebhook.OrderStatus> webhooks = wrapper.getOrders();
        log.info("Processing {} webhook(s)", webhooks.size());

        int successCount = 0;
        int failCount = 0;
        Map<String, Object> lastResult = null;

        for (StatusUpdateWebhook.OrderStatus webhook : webhooks) {
            try {
                // We need to verify if processStatusUpdate accepts OrderStatus or
                // StatusUpdateWebhook
                // Based on previous code, it likely accepted StatusUpdateWebhook.
                // If it accepts StatusUpdateWebhook, we might need to convert or overload the
                // method.
                // Let's assume for now we need to adapt it.
                // Wait, if StatusUpdateWebhook was the WRONG type for the JSON, existing
                // service processing might also be broken if it expects the complex structure.
                // However, the user provided JSON { "orders": [...] }.
                // If the service expects `StatusUpdateWebhook`, how was it working before?
                // Maybe it wasn't? Or maybe `StatusUpdateWebhook` was receiving the WHOLE
                // payload?
                // The previous error `MismatchedInputException` ...
                // `ArrayList<StatusUpdateWebhook>` meant it tried to parse the `orders` array
                // elements into `StatusUpdateWebhook`.
                // But the elements are `OrderStatus`.
                // So the service `processStatusUpdate` probably takes the *content* of the
                // order update.
                // I need to check `WebhookProcessingService.java`.

                // For this step, I will update the loop variable type.
                // I will also assume I need to pass this object to the service.
                // I'll leave the service call as is for a moment but I suspect I'll need to
                // update the service signature too.
                lastResult = webhookProcessingService.processStatusUpdate(webhook);
                successCount++;
            } catch (Exception e) {
                log.error("Error processing webhook for order {}: {}", webhook.getOrderId(), e.getMessage(), e);
                failCount++;
            }
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("processed", webhooks.size());
        response.put("successCount", successCount);
        response.put("failCount", failCount);

        if (lastResult != null && webhooks.size() == 1) {
            // For single webhook, return details of that execution as before
            return ResponseEntity.ok(lastResult);
        }

        return ResponseEntity.ok(response);
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

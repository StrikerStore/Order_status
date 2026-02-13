package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom deserializer for WebhookBody to handle cases where the body might be:
 * 1. An object (normal case)
 * 2. A string (error case - will create empty body and log warning)
 * 3. Missing/null (will create empty body)
 */
public class WebhookBodyDeserializer extends JsonDeserializer<StatusUpdateWebhook.WebhookBody> {

    private static final Logger log = LoggerFactory.getLogger(WebhookBodyDeserializer.class);

    @Override
    public StatusUpdateWebhook.WebhookBody deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);
        
        StatusUpdateWebhook.WebhookBody body = new StatusUpdateWebhook.WebhookBody();
        
        // If node is null, return empty body
        if (node == null) {
            log.warn("WebhookBody node is null, creating empty body");
            return body;
        }
        
        // If node is a string (unexpected), log warning and return empty body
        if (node.isTextual()) {
            log.warn("WebhookBody is a string value '{}' instead of an object. This might indicate a JSON structure mismatch.", node.asText());
            return body;
        }
        
        // If node is not an object, return empty body
        if (!node.isObject()) {
            log.warn("WebhookBody is not an object (type: {}), creating empty body", node.getNodeType());
            return body;
        }
        
        // Deserialize timestamp
        if (node.has("timestamp")) {
            body.setTimestamp(node.get("timestamp").asText());
        }
        
        // Deserialize event
        if (node.has("event")) {
            body.setEvent(node.get("event").asText());
        }
        
        // Deserialize orders array
        if (node.has("orders") && node.get("orders").isArray()) {
            JsonNode ordersNode = node.get("orders");
            List<StatusUpdateWebhook.OrderStatus> orders = new ArrayList<>();
            
            for (JsonNode orderNode : ordersNode) {
                try {
                    StatusUpdateWebhook.OrderStatus order = mapper.treeToValue(orderNode, StatusUpdateWebhook.OrderStatus.class);
                    orders.add(order);
                } catch (Exception e) {
                    log.warn("Failed to deserialize order entry: {}", e.getMessage());
                    // Skip invalid order entries
                    continue;
                }
            }
            
            body.setOrders(orders);
        }
        
        return body;
    }
}

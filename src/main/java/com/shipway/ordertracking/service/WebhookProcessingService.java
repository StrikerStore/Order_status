package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    @Autowired
    private OrderCreatedFlowService orderCreatedFlowService;

    @Autowired
    private ShopifyFulfillmentFlowService shopifyFulfillmentFlowService;

    @Autowired
    private InTransitFlowService inTransitFlowService;

    @Autowired
    private OutForDeliveryFlowService outForDeliveryFlowService;

    @Autowired
    private DeliveredFlowService deliveredFlowService;

    /**
     * Process status update webhook and route to appropriate flow service
     */
    /**
     * Process status update webhook for a single order and route to appropriate
     * flow service
     */
    public Map<String, Object> processStatusUpdate(StatusUpdateWebhook.OrderStatus order) {
        Map<String, Object> result = new HashMap<>();

        if (order == null) {
            result.put("success", false);
            result.put("message", "Invalid order: null");
            return result;
        }

        try {
            boolean processed = routeToFlowService(order);
            result.put("success", processed);
            result.put("message", processed ? "Order processed successfully" : "Order processing skipped or failed");
            result.put("orderId", order.getOrderId());
        } catch (Exception e) {
            log.error("Error processing order {}: {}", order.getOrderId(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error processing order: " + e.getMessage());
        }

        return result;
    }

    /**
     * Route order to appropriate flow service based on current_shipment_status
     */
    private boolean routeToFlowService(StatusUpdateWebhook.OrderStatus order) {
        String status = order.getCurrentShipmentStatus();
        String orderId = order.getOrderId();
        String phone = order.getShippingPhone();

        log.debug("Routing order {} with status: {}", orderId, status);

        if (phone == null || phone.isEmpty()) {
            log.warn("Skipping order {}: missing shipping phone", orderId);
            return false;
        }

        // Normalize status for comparison (handle both space-separated and underscore
        // formats)
        String normalizedStatus = status != null ? status.trim().toUpperCase().replace("_", " ") : "";

        // Route based on current_shipment_status
        // Based on sample JSON: "Out for Delivery", "Delivered", "In Transit",
        // "OUT_FOR_PICKUP", etc.
        if (normalizedStatus.contains("OUT FOR DELIVERY")) {
            return outForDeliveryFlowService.processOutForDelivery(order);
        } else if (normalizedStatus.equals("DELIVERED")) {
            return deliveredFlowService.processDelivered(order);
        } else if (normalizedStatus.contains("IN TRANSIT") || normalizedStatus.contains("PICKED UP")) {
            return inTransitFlowService.processInTransit(order);
        } else if (normalizedStatus.contains("SHIPMENT BOOKED") || normalizedStatus.contains("OUT FOR PICKUP")
                || normalizedStatus.contains("SHPFR1") || normalizedStatus.contains("SHIPPED")) {
            // Route to shopify fulfillment flow for shipped/fulfilled orders
            return shopifyFulfillmentFlowService.processShopifyFulfillment(order);
        } else if (normalizedStatus.contains("RTO") || normalizedStatus.contains("RETURN TO ORIGIN")) {
            // Handle RTO case - you may want to create a separate service for this
            log.info("RTO status detected for order {}, but no RTO flow service implemented yet", orderId);
            return false;
        } else {
            log.warn("Unknown status '{}' for order {}, skipping", status, orderId);
            return false;
        }
    }
}

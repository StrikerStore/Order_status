package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.StatusUpdateWebhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.config.ShopifyProperties;

@Service
public class ShopifyFulfillmentFlowService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyFulfillmentFlowService.class);

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private ShopifyProperties shopifyProperties;

    /**
     * Process shopify fulfillment webhook
     * Handles fulfillment creation/update and sends Botspace notification
     */
    public boolean processShopifyFulfillment(StatusUpdateWebhook.OrderStatus order) {
        log.info("Processing shopify fulfillment flow for order: {}", order.getOrderId());

        // Validate required fields
        String accountCode = order.getAccountCode();
        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Account code is missing for order: {}", order.getOrderId());
            return false;
        }

        String orderId = order.getOrderId();

        // CHECK: Clone order detection
        // Move check BEFORE lookup
        if (orderId != null && orderId.contains("_")) {
            log.info(
                    "Order {} detected as clone (contains '_'), stopping fulfillment flow completely as per requirement",
                    orderId);
            return false;
        }

        // 1) GraphQL: get order with displayFulfillmentStatus and name
        Map<String, Object> orderNode = shopifyService.getOrderWithDisplayFulfillmentStatus(accountCode, orderId);
        if (orderNode == null) {
            log.warn("Order {} not found via GraphQL (account: {})", orderId, accountCode);
            return false;
        }

        String displayStatus = orderNode.get("displayFulfillmentStatus") != null
                ? orderNode.get("displayFulfillmentStatus").toString()
                : null;

        if ("FULFILLED".equalsIgnoreCase(displayStatus)) {
            return processFulfillmentWhenFulfilled(accountCode, orderId, order, orderNode);
        }

        // UNFULFILLED or null: get fulfillment orders, find OPEN, create fulfillment if
        // needed
        String orderGid = orderNode.get("id") != null ? orderNode.get("id").toString() : null;
        if (orderGid == null) {
            log.warn("Order {} has no id in GraphQL response", orderId);
            return false;
        }

        Map<String, Object> fulfillmentOrdersData = shopifyService.getFulfillmentOrdersForOrder(accountCode, orderGid);
        if (fulfillmentOrdersData == null) {
            log.warn("Could not get fulfillment orders for order {} (account: {})", orderId, accountCode);
            return false;
        }

        String openFulfillmentOrderId = shopifyService.getOpenFulfillmentOrderIdFromEdges(fulfillmentOrdersData);
        if (openFulfillmentOrderId == null || openFulfillmentOrderId.isEmpty()) {
            log.warn("No OPEN fulfillment order for order {} (account: {}), skipping fulfillment flow", orderId,
                    accountCode);
            return false;
        }

        Long numericOrderId = com.shipway.ordertracking.service.ShopifyService.parseNumericIdFromGid(orderGid,
                "gid://shopify/Order/");
        if (numericOrderId == null) {
            log.warn("Could not parse numeric order ID from {}", orderGid);
            return false;
        }

        String trackingUrl = buildTrackingUrl(order);
        Long fulfillmentId = shopifyService.createFulfillment(accountCode, numericOrderId, openFulfillmentOrderId,
                order.getAwb(), trackingUrl);

        if (fulfillmentId == null) {
            log.warn("Creation returned null, checking for existing fulfillment ID for order {}", orderId);
            fulfillmentId = shopifyService.getFulfillmentId(accountCode, numericOrderId);
        }

        if (fulfillmentId == null) {
            log.warn("Failed to create/find fulfillment for order {} (account: {})", orderId, accountCode);
            return false;
        }

        // Use the ID directly for tracking update
        if (!shopifyService.updateFulfillmentTracking(accountCode, numericOrderId, fulfillmentId, order.getAwb(),
                "fulfilled")) {
            log.error("Failed to update fulfillment tracking for order {}, stopping flow", orderId);
            return false;
        }

        log.info("✅ Shopify fulfillment flow completed successfully for order: {}", orderId);
        return true;
    }

    private boolean processFulfillmentWhenFulfilled(String accountCode, String orderId,
            StatusUpdateWebhook.OrderStatus order, Map<String, Object> orderNode) {
        Long numericOrderId = com.shipway.ordertracking.service.ShopifyService.parseNumericIdFromGid(
                orderNode.get("id") != null ? orderNode.get("id").toString() : null, "gid://shopify/Order/");
        if (numericOrderId == null) {
            log.warn("Could not parse numeric order ID for order {}", orderId);
            return false;
        }

        Object fulfillmentsObj = orderNode.get("fulfillments");
        Long fulfillmentId = null;
        if (fulfillmentsObj instanceof List && !((List<?>) fulfillmentsObj).isEmpty()) {
            Object first = ((List<?>) fulfillmentsObj).get(0);
            if (first instanceof Map) {
                Object idObj = ((Map<?, ?>) first).get("id");
                if (idObj != null) {
                    fulfillmentId = com.shipway.ordertracking.service.ShopifyService.parseNumericIdFromGid(
                            idObj.toString(), "gid://shopify/Fulfillment/");
                }
            }
        }
        if (fulfillmentId != null) {
            if (!shopifyService.updateFulfillmentTracking(accountCode, numericOrderId, fulfillmentId, order.getAwb(),
                    "fulfilled")) {
                log.error("Failed to update fulfillment tracking (fulfilled path) for order {}, stopping flow",
                        orderId);
                return false;
            }
        }

        log.info("✅ Shopify fulfillment flow completed (already fulfilled) for order: {}", orderId);
        return true;
    }

    /**
     * Build tracking URL from AWB using account-specific template
     */
    private String buildTrackingUrl(StatusUpdateWebhook.OrderStatus order) {
        if (order.getAwb() != null && !order.getAwb().isEmpty()) {
            String accountCode = order.getAccountCode();
            ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);

            if (account != null && account.getTrackingUrlTemplate() != null
                    && !account.getTrackingUrlTemplate().isEmpty()) {
                // Replace {awb} placeholder with actual AWB
                return account.getTrackingUrlTemplate().replace("{awb}", order.getAwb());
            }

            // Fallback if no template configured
            return "https://tracking.example.com/track/" + order.getAwb();
        }
        return null;
    }
}

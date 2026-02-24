package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.util.PhoneNumberUtil;

@Service
public class InTransitFlowService {

    private static final Logger log = LoggerFactory.getLogger(InTransitFlowService.class);

    @Autowired
    private BotspaceService botspaceService;

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private BotspaceProperties botspaceProperties;

    @Autowired
    private com.shipway.ordertracking.config.ShopifyProperties shopifyProperties;

    @Autowired
    private CustomerMessageTrackingService customerMessageTrackingService;

    private static final List<String> IN_TRANSIT_STATUSES = Arrays.asList("sent_inTransit", "failed_inTransit");

    /**
     * Process in transit webhook.
     * 1) Get order via GraphQL (displayFulfillmentStatus + tags).
     * 2) If FULFILLED: check tag AAA_INTRANIST; if missing, update tracking, add
     * tag, send Botspace.
     * 3) If UNFULFILLED/null: get fulfillment orders, if OPEN exists create
     * fulfillment, then same as FULFILLED.
     */
    public boolean processInTransit(StatusUpdateWebhook.OrderStatus order) {
        log.info("Processing in transit flow for order: {}", order.getOrderId());

        // CHECK 1: Status change validation - skip if status hasn't changed
        String currentStatus = order.getCurrentShipmentStatus();
        String previousStatus = order.getPreviousStatus();
        if (currentStatus != null && previousStatus != null) {
            String normalizedCurrent = currentStatus.trim().toUpperCase().replace("_", " ");
            String normalizedPrevious = previousStatus.trim().toUpperCase().replace("_", " ");
            if (normalizedCurrent.equals(normalizedPrevious)) {
                log.info("Order {} status unchanged (current: {}, previous: {}), skipping in transit flow",
                        order.getOrderId(), currentStatus, previousStatus);
                return true; // Not an error, just no change
            }
        }

        String customerPhone = order.getShippingPhone();
        if (customerPhone == null || customerPhone.isEmpty()) {
            log.warn("Customer phone is missing for order: {}", order.getOrderId());
            return false;
        }

        String accountCode = order.getAccountCode();
        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Account code is missing for order: {}", order.getOrderId());
            return false;
        }

        String orderId = order.getOrderId();

        // CHECK 2: Database check - skip if already processed (sent or failed)
        if (customerMessageTrackingService.hasAnyStatus(orderId, accountCode, IN_TRANSIT_STATUSES)) {
            log.info("Order {} already has in transit status in database, skipping in transit flow", orderId);
            return true;
        }

        // CHECK 3: Clone order detection - skip fulfillment, send Botspace only (DB add happens in sendTemplateMessage)
        if (orderId != null && orderId.contains("_")) {
            log.info(
                    "Order {} detected as clone (contains '_'), skipping Shopify lookup and fulfillment. Sending Botspace.",
                    orderId);
            return sendInTransitBotspaceMessage(accountCode, order);
        }

        // 1) GraphQL: get order with displayFulfillmentStatus
        Map<String, Object> orderNode = shopifyService.getOrderWithDisplayFulfillmentStatus(accountCode, orderId);
        if (orderNode == null) {
            log.warn("Order {} not found via GraphQL (account: {})", orderId, accountCode);
            return false;
        }

        String displayStatus = orderNode.get("displayFulfillmentStatus") != null
                ? orderNode.get("displayFulfillmentStatus").toString()
                : null;

        if ("FULFILLED".equalsIgnoreCase(displayStatus)) {
            return processInTransitWhenFulfilled(accountCode, orderId, order, orderNode);
        }

        // UNFULFILLED or null: get fulfillment orders, find OPEN, create fulfillment if
        // needed, then proceed
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
            log.warn("No OPEN fulfillment order for order {} (account: {}), skipping in-transit flow", orderId,
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
                "in_transit")) {
            log.error("Failed to update fulfillment tracking for order {}, stopping flow", orderId);
            return false;
        }

        return proceedWithTagAndBotspace(accountCode, orderId, numericOrderId, order, fulfillmentOrdersData);
    }

    private boolean processInTransitWhenFulfilled(String accountCode, String orderId,
            StatusUpdateWebhook.OrderStatus order, Map<String, Object> orderNode) {
        if (customerMessageTrackingService.hasAnyStatus(orderId, accountCode, IN_TRANSIT_STATUSES)) {
            log.info("Order {} already has in transit status in database, skipping in transit flow", orderId);
            return true;
        }

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
                    "in_transit")) {
                log.error("Failed to update fulfillment tracking (fulfilled path) for order {}, stopping flow",
                        orderId);
                return false;
            }
        }

        return sendInTransitBotspaceMessage(accountCode, order);
    }

    private boolean proceedWithTagAndBotspace(String accountCode, String orderId, Long numericOrderId,
            StatusUpdateWebhook.OrderStatus order, Map<String, Object> orderDataWithTags) {
        if (customerMessageTrackingService.hasAnyStatus(orderId, accountCode, IN_TRANSIT_STATUSES)) {
            log.info("Order {} already has in transit status in database, skipping in transit notification", orderId);
            return true;
        }
        return sendInTransitBotspaceMessage(accountCode, order);
    }

    private boolean sendInTransitBotspaceMessage(String accountCode, StatusUpdateWebhook.OrderStatus order) {
        String templateId = getTemplateIdForAccount(accountCode);
        if (templateId == null || templateId.isEmpty()) {
            log.warn("Template ID not configured for account code: {} (order: {})", accountCode, order.getOrderId());
            return false;
        }

        String formattedPhone = PhoneNumberUtil.formatPhoneNumber(order.getShippingPhone());
        List<String> variables = buildTemplateVariables(order);
        String trackingUrl = buildTrackingUrl(order);

        BotspaceMessageRequest request = new BotspaceMessageRequest();
        request.setPhone(formattedPhone);
        request.setTemplateId(templateId);
        request.setVariables(variables);
        if (trackingUrl != null && !trackingUrl.isEmpty()) {
            request.setMediaVariable(trackingUrl);
            List<BotspaceMessageRequest.Card> cards = new ArrayList<>();
            cards.add(new BotspaceMessageRequest.Card(trackingUrl));
            request.setCards(cards);
        }

        boolean sent = botspaceService.sendTemplateMessage(accountCode, request, order.getOrderId(), "sent_inTransit",
                "failed_inTransit");
        if (sent) {
            log.info("✅ In transit notification sent successfully for order: {} to phone: {}", order.getOrderId(),
                    formattedPhone);
        } else {
            log.error("❌ Failed to send in transit notification for order: {}", order.getOrderId());
        }
        return sent;
    }

    /**
     * Build template variables array
     * Order of variables should match the template variable order in Botspace
     * TODO: Update variable order and values based on your template requirements
     */
    private List<String> buildTemplateVariables(StatusUpdateWebhook.OrderStatus order) {
        List<String> variables = new ArrayList<>();

        // Variable 1: Customer first name
        String firstName = order.getShippingFirstname() != null ? order.getShippingFirstname() : "";
        variables.add(firstName);

        // Variable 2: Number of products
        String numberOfProducts = order.getNumberOfProduct() != null ? order.getNumberOfProduct().toString() : "0";
        variables.add(numberOfProducts);

        // Variable 3: Order ID/Name
        String orderId = order.getOrderId() != null ? order.getOrderId() : "";
        variables.add(orderId);

        // Variable 4: Tracking URL
        String trackingUrl = buildTrackingUrl(order);
        if (trackingUrl != null) {
            variables.add(trackingUrl);
        }

        // Add more variables as needed based on your template

        return variables;
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

    /**
     * Get template ID for in transit status based on account code
     */
    private String getTemplateIdForAccount(String accountCode) {
        // Get the Botspace account for this order
        BotspaceAccount botspaceAccount = botspaceProperties
                .getAccountByCode(accountCode);

        if (botspaceAccount != null && botspaceAccount.getInTransitTemplateId() != null) {
            return botspaceAccount.getInTransitTemplateId();
        }

        log.warn("In transit template ID not found for account code: {}", accountCode);
        return null;
    }

    /**
     * Update Shopify order status to "in_transit"
     */
    private void updateShopifyStatus(StatusUpdateWebhook.OrderStatus order) {
        try {
            String accountCode = order.getAccountCode();
            String orderId = order.getOrderId();
            String trackingNumber = order.getAwb();
            String status = order.getCurrentShipmentStatus();

            if (orderId != null && !orderId.isEmpty()) {
                if (accountCode == null || accountCode.isEmpty()) {
                    log.warn("Cannot update Shopify status: account code is missing for order: {}", orderId);
                    return;
                }

                boolean updated = shopifyService.updateOrderStatus(accountCode, orderId, trackingNumber, status);
                if (updated) {
                    log.info("✅ Shopify status updated successfully for order: {} (account: {})", orderId, accountCode);
                } else {
                    log.warn("⚠️ Failed to update Shopify status for order: {} (account: {})", orderId, accountCode);
                }
            } else {
                log.warn("Cannot update Shopify status: order ID is missing");
            }
        } catch (Exception e) {
            log.error("❌ Error updating Shopify status for order {}: {}",
                    order.getOrderId(), e.getMessage(), e);
            // Don't fail the entire flow if Shopify update fails
        }
    }

}

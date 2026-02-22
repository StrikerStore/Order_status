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
import com.shipway.ordertracking.config.ShopifyProperties;

@Service
public class DeliveredFlowService {

    private static final Logger log = LoggerFactory.getLogger(DeliveredFlowService.class);

    @Autowired
    private BotspaceService botspaceService;

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private BotspaceProperties botspaceProperties;

    @Autowired
    ShopifyProperties shopifyProperties;

    @Autowired
    private CustomerMessageTrackingService customerMessageTrackingService;

    private static final List<String> DELIVERED_STATUSES = Arrays.asList("sent_delivered", "failed_delivered");

    /**
     * Process delivered webhook
     * 1) GraphQL: Get order with status & tags
     * 2) If FULFILLED: check tag AAA_DELIVERED; if missing, update tracking, add
     * tag, send Botspace.
     * 3) If UNFULFILLED/null: get fulfillment orders, if OPEN exists create
     * fulfillment, then same as FULFILLED.
     */
    public boolean processDelivered(StatusUpdateWebhook.OrderStatus order) {
        log.info("Processing delivered flow for order: {}", order.getOrderId());

        // CHECK 1: Status change validation - skip if status hasn't changed
        String currentStatus = order.getCurrentShipmentStatus();
        String previousStatus = order.getPreviousStatus();
        if (currentStatus != null && previousStatus != null) {
            String normalizedCurrent = currentStatus.trim().toUpperCase().replace("_", " ");
            String normalizedPrevious = previousStatus.trim().toUpperCase().replace("_", " ");
            if (normalizedCurrent.equals(normalizedPrevious)) {
                log.info("Order {} status unchanged (current: {}, previous: {}), skipping delivered flow",
                        order.getOrderId(), currentStatus, previousStatus);
                return true; // Not an error, just no change
            }
        }

        // Validate required fields
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
        if (customerMessageTrackingService.hasAnyStatus(orderId, accountCode, DELIVERED_STATUSES)) {
            log.info("Order {} already has delivered status in database, skipping delivered flow", orderId);
            return true;
        }

        // CHECK 3: Clone order detection - skip fulfillment, send Botspace only (DB add happens in sendTemplateMessage)
        if (orderId != null && orderId.contains("_")) {
            log.info(
                    "Order {} detected as clone (contains '_'), skipping Shopify lookup and fulfillment. Sending Botspace.",
                    orderId);
            return sendDeliveredBotspaceMessage(accountCode, orderId, order);
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
            return processDeliveredWhenFulfilled(accountCode, orderId, order, orderNode);
        }

        // UNFULFILLED or null: get fulfillment orders, find OPEN, create fulfillment if
        // needed, then proceed
        String orderGid = orderNode.get("id") != null ? orderNode.get("id").toString() : null;
        if (orderGid == null) {
            log.warn("Order {} has no id in GraphQL response", orderId);
            return false;
        }

        Map<String, Object> fulfillmentOrdersData = shopifyService.getFulfillmentOrdersForOrder(accountCode,
                orderGid);
        if (fulfillmentOrdersData == null) {
            log.warn("Could not get fulfillment orders for order {} (account: {})", orderId, accountCode);
            return false;
        }

        String openFulfillmentOrderId = shopifyService.getOpenFulfillmentOrderIdFromEdges(fulfillmentOrdersData);
        if (openFulfillmentOrderId == null || openFulfillmentOrderId.isEmpty()) {
            log.warn("No OPEN fulfillment order for order {} (account: {}), skipping delivered flow", orderId,
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

        // Optimization: For newly created fulfillment, tracking should be correct.
        // Update tracking to delivered
        if (!shopifyService.updateFulfillmentTracking(accountCode, numericOrderId, fulfillmentId, order.getAwb(),
                "delivered")) {
            log.error("Failed to update fulfillment tracking to delivered for order {}, stopping flow", orderId);
            return false;
        }

        return proceedWithTagAndBotspace(accountCode, orderId, numericOrderId, order, fulfillmentOrdersData);
    }

    private boolean processDeliveredWhenFulfilled(String accountCode, String orderId,
            StatusUpdateWebhook.OrderStatus order, Map<String, Object> orderNode) {
        if (customerMessageTrackingService.hasAnyStatus(orderId, accountCode, DELIVERED_STATUSES)) {
            log.info("Order {} already has delivered status in database, skipping delivered flow", orderId);
            return true;
        }

        Long numericOrderId = com.shipway.ordertracking.service.ShopifyService.parseNumericIdFromGid(
                orderNode.get("id") != null ? orderNode.get("id").toString() : null, "gid://shopify/Order/");
        if (numericOrderId == null) {
            log.warn("Could not parse numeric order ID for order {}", orderId);
            return false;
        }

        // Get fulfillment ID
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
            // Verify if tracking number needs update (Optimization)
            String currentTrackingNumber = null;
            if (fulfillmentsObj instanceof List) {
                for (Object f : (List<?>) fulfillmentsObj) {
                    if (f instanceof Map) {
                        Object idObj = ((Map<?, ?>) f).get("id");
                        if (idObj != null && String.valueOf(idObj).endsWith("/" + fulfillmentId)) {
                            // Found the fulfillment, check tracking
                            Object trackingInfoObj = ((Map<?, ?>) f).get("trackingInfo");
                            if (trackingInfoObj instanceof List && !((List<?>) trackingInfoObj).isEmpty()) {
                                Object firstTracking = ((List<?>) trackingInfoObj).get(0);
                                if (firstTracking instanceof Map) {
                                    Object numObj = ((Map<?, ?>) firstTracking).get("number");
                                    if (numObj != null) {
                                        currentTrackingNumber = numObj.toString();
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }

            String trackingNumberToUpdate = order.getAwb();
            if (currentTrackingNumber != null && currentTrackingNumber.equals(order.getAwb())) {
                log.info("Tracking number {} already set for fulfillment {}, skipping REST update",
                        currentTrackingNumber, fulfillmentId);
                trackingNumberToUpdate = null; // Skip REST update
            }

            if (!shopifyService.updateFulfillmentTracking(accountCode, numericOrderId, fulfillmentId,
                    trackingNumberToUpdate, "delivered")) {
                log.error("Failed to update fulfillment tracking (fulfilled path) for order {}, stopping flow",
                        orderId);
                return false;
            }
        } else {
            log.warn("Order {} is fulfilled but no fulfillment ID found, cannot update tracking", orderId);
            return false;
        }

        return sendDeliveredBotspaceMessage(accountCode, orderId, order);
    }

    private boolean proceedWithTagAndBotspace(String accountCode, String orderId, Long numericOrderId,
            StatusUpdateWebhook.OrderStatus order, Map<String, Object> orderDataWithTags) {
        if (customerMessageTrackingService.hasAnyStatus(orderId, accountCode, DELIVERED_STATUSES)) {
            log.info("Order {} already has delivered status in database, skipping delivered notification", orderId);
            return true;
        }
        return sendDeliveredBotspaceMessage(accountCode, orderId, order);
    }

    private boolean sendDeliveredBotspaceMessage(String accountCode, String orderId,
            StatusUpdateWebhook.OrderStatus order) {
        // Get template ID
        String templateId = getTemplateIdForAccount(accountCode);
        if (templateId == null || templateId.isEmpty()) {
            log.warn("Template ID not configured for account code: {} (order: {})", accountCode, order.getOrderId());
            return false;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        String productUrlPrefix = account != null ? account.getProductUrl() : null;
        if (productUrlPrefix == null) {
            log.warn("Product URL prefix not configured for account code: {}", accountCode);
            productUrlPrefix = "https://www.thestrikerstore.com/products/"; // Default fallback, though should be
                                                                            // configured

        }

        // Call Shopify API to get product details (handle and image)
        List<ShopifyService.ProductDetails> productDetails = shopifyService.getOrderProductDetails(accountCode,
                orderId);
        log.info("Retrieved {} product details for order {} (account: {})", productDetails.size(), orderId,
                accountCode);

        String formattedPhone = formatPhoneNumber(order.getShippingPhone());

        // Construct product URL using the first product's handle
        String productUrl = productUrlPrefix; // Fallback to just prefix if no handle

        if (!productDetails.isEmpty()) {
            ShopifyService.ProductDetails firstProduct = productDetails.get(0);
            if (firstProduct.getHandle() != null) {
                productUrl = productUrlPrefix + firstProduct.getHandle() + "#judgeme";
            }
        }

        // Variables: [OrderName, ProductURL, ProductURL]
        List<String> variables = new ArrayList<>();
        variables.add(orderId);
        variables.add(productUrl);
        variables.add(productUrl);

        BotspaceMessageRequest request = new BotspaceMessageRequest();
        request.setPhone(formattedPhone);
        request.setTemplateId(templateId);
        request.setVariables(variables);

        // User requested to use product handle (URL) in place of image URL for
        // mediaVariable and cards
        if (!productDetails.isEmpty()) {
            // For the first item, use the constructed URL as mediaVariable
            request.setMediaVariable(productUrl);

            // Create cards for all products
            List<BotspaceMessageRequest.Card> cards = new ArrayList<>();
            for (ShopifyService.ProductDetails details : productDetails) {
                if (details.getHandle() != null) {
                    String cardUrl = productUrlPrefix + details.getHandle() + "#judgeme";
                    BotspaceMessageRequest.Card card = new BotspaceMessageRequest.Card(cardUrl);
                    cards.add(card);
                }
            }
            if (!cards.isEmpty()) {
                request.setCards(cards);
            }
        }

        boolean sent = botspaceService.sendTemplateMessage(accountCode, request, orderId, "sent_delivered",
                "failed_delivered");
        if (sent) {
            log.info("✅ Delivered notification sent successfully for order: {} to phone: {}", orderId, formattedPhone);
        } else {
            log.error("❌ Failed to send delivered notification for order: {}", orderId);
        }
        return sent;
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null)
            return "";
        // Remove non-digit characters
        String digits = phone.replaceAll("\\D", "");
        // If it looks like Indian number (10 digits) and no prefix, add 91
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits; // Or whatever fallback logic is needed
    }

    private List<String> buildTemplateVariables(StatusUpdateWebhook.OrderStatus order) {
        List<String> variables = new ArrayList<>();
        String firstName = order.getShippingFirstname() != null ? order.getShippingFirstname() : "";
        variables.add(firstName);
        String orderId = order.getOrderId() != null ? order.getOrderId() : "";
        variables.add(orderId);
        return variables;
    }

    private String getTemplateIdForAccount(String accountCode) {
        // Get Botspace account config
        BotspaceAccount botspaceAccount = botspaceProperties.getAccountByCode(accountCode);
        if (botspaceAccount != null && botspaceAccount.getDeliveredTemplateId() != null) {
            return botspaceAccount.getDeliveredTemplateId();
        }
        return null;
    }

    /**
     * Build tracking URL from AWB using account-specific template
     */
    private String buildTrackingUrl(StatusUpdateWebhook.OrderStatus order) {
        if (order.getAwb() != null && !order.getAwb().isEmpty()) {
            String accountCode = order.getAccountCode();
            com.shipway.ordertracking.config.ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);

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
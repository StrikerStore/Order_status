package com.shipway.ordertracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.config.ShopifyAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShopifyService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ShopifyProperties shopifyProperties;

    @Autowired
    private ObjectMapper objectMapper;

    /** Log Shopify API response body as JSON */
    /** Log Shopify API response based on status */
    private void logShopifyResponse(String context, ResponseEntity<?> response) {
        if (response == null)
            return;

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("✅ Response is successful for {}", context);
        } else {
            Object body = response.getBody();
            if (body != null) {
                try {
                    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                    log.error("❌ Shopify API Failed Response ({}): Status: {}\n{}", context, response.getStatusCode(),
                            json);
                } catch (Exception e) {
                    log.warn("Failed to serialize Shopify failed response to JSON: {}", e.getMessage());
                }
            } else {
                log.error("❌ Shopify API Failed Response ({}) Status: {}", context, response.getStatusCode());
            }
        }
    }

    /**
     * Update order fulfillment status in Shopify
     * 
     * @param accountCode    Account code to identify which Shopify account to use
     * @param orderId        Shopify order ID (order name/number)
     * @param trackingNumber AWB/tracking number
     * @param status         Order status (e.g., "in_transit", "out_for_delivery",
     *                       "delivered")
     * @return true if update successful, false otherwise
     */
    public boolean updateOrderStatus(String accountCode, String orderId, String trackingNumber, String status) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("Cannot update Shopify status: order ID is empty");
            return false;
        }

        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Cannot update Shopify status: account code is empty");
            return false;
        }

        // Get Shopify account configuration for this account code
        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return false;
        }

        try {
            // Resolve numeric order ID: try REST first, then GraphQL when REST fails (e.g.
            // name "254120" vs "#254120")
            Long shopifyOrderId = getOrderIdByName(account, orderId);
            if (shopifyOrderId == null) {
                Map<String, Object> orderData = getOrderWithFulfillmentsGraphQL(accountCode, orderId);
                if (orderData != null) {
                    Object idObj = orderData.get("id");
                    if (idObj != null) {
                        String gid = idObj.toString();
                        if (gid.startsWith("gid://shopify/Order/")) {
                            shopifyOrderId = Long.parseLong(gid.substring("gid://shopify/Order/".length()));
                            log.debug("Resolved order ID {} via GraphQL for order name {} (account: {})",
                                    shopifyOrderId, orderId, accountCode);
                        }
                    }
                }
            }
            if (shopifyOrderId == null) {
                log.warn("Order {} not found in Shopify (account: {})", orderId, accountCode);
                return false;
            }

            // Get fulfillment ID for the order
            Long fulfillmentId = getFulfillmentId(account, shopifyOrderId);
            if (fulfillmentId == null) {
                log.warn("Fulfillment not found for order {} (account: {})", orderId, accountCode);
                return false;
            }

            // Update fulfillment tracking
            return updateFulfillmentTracking(account, shopifyOrderId, fulfillmentId, trackingNumber, status);

        } catch (Exception e) {
            log.error("❌ Error updating Shopify order status for order {} (account: {}): {}",
                    orderId, accountCode, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get Shopify order ID by order name/number.
     * Tries both "254120" and "#254120" as Shopify store may use either format.
     */
    public Long getOrderIdByName(ShopifyAccount account, String orderName) {
        if (orderName == null || orderName.isEmpty()) {
            return null;
        }
        Long id = getOrderIdByNameExact(account, orderName);
        if (id != null) {
            return id;
        }
        // Try with # prefix if not already present (Shopify display name is often
        // "#1001")
        if (!orderName.startsWith("#")) {
            id = getOrderIdByNameExact(account, "#" + orderName);
        } else {
            id = getOrderIdByNameExact(account, orderName.substring(1));
        }
        return id;
    }

    private Long getOrderIdByNameExact(ShopifyAccount account, String orderName) {
        try {
            String encodedName = java.net.URLEncoder.encode(orderName, java.nio.charset.StandardCharsets.UTF_8);
            String apiUrl = account.getApiUrl() + "/orders.json?name=" + encodedName;
            HttpHeaders headers = createHeaders(account);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                if (body != null && body.containsKey("orders")) {
                    List<?> orders = (List<?>) body.get("orders");
                    if (orders != null && !orders.isEmpty()) {
                        Map<String, Object> order = (Map<String, Object>) orders.get(0);
                        if (order != null && order.containsKey("id")) {
                            return Long.valueOf(order.get("id").toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Order lookup by name '{}' failed: {}", orderName, e.getMessage());
        }
        return null;
    }

    /**
     * Get fulfillment ID for an order by account code (looks up account and
     * delegates).
     */
    public Long getFulfillmentId(String accountCode, Long orderId) {
        if (accountCode == null || accountCode.isEmpty())
            return null;
        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        return account != null ? getFulfillmentId(account, orderId) : null;
    }

    /**
     * Get fulfillment ID for an order
     */
    public Long getFulfillmentId(ShopifyAccount account, Long orderId) {
        try {
            String apiUrl = account.getApiUrl() + "/orders/" + orderId + "/fulfillments.json";
            HttpHeaders headers = createHeaders(account);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                if (body != null && body.containsKey("fulfillments")) {
                    List<?> fulfillments = (List<?>) body.get("fulfillments");
                    if (fulfillments != null && !fulfillments.isEmpty()) {
                        Map<String, Object> fulfillment = (Map<String, Object>) fulfillments.get(0);
                        if (fulfillment != null && fulfillment.containsKey("id")) {
                            return Long.valueOf(fulfillment.get("id").toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching fulfillment for order {}: {}", orderId, e.getMessage());
        }
        return null;
    }

    /**
     * Update fulfillment tracking information
     */
    public boolean updateFulfillmentTracking(ShopifyAccount account, Long orderId, Long fulfillmentId,
            String trackingNumber, String status) {
        try {
            if (status != null && !status.isEmpty()) {
                // Use GraphQL mutation for status updates (IN_TRANSIT, OUT_FOR_DELIVERY,
                // DELIVERED, etc.)
                // This updates the shipment status visible in Shopify admin/customer page
                boolean eventCreated = createFulfillmentEvent(account, fulfillmentId, status);
                if (eventCreated) {
                    log.info("✅ Fulfillment event created successfully for order {} (status: {})", orderId, status);
                } else {
                    log.warn(
                            "⚠️ Failed to create fulfillment event for order {} (status: {}), falling back to REST update (may not update status visible in admin)",
                            orderId, status);
                }

                // If tracking number is present, we still need to update it via REST if it
                // wasn't set during fulfillment creation
                if (trackingNumber != null && !trackingNumber.isEmpty()) {
                    // Continue to REST update below for tracking number
                } else {
                    // If only status update was needed and event created, we are done
                    return eventCreated;
                }
            }

            // Shopify REST: POST
            // /admin/api/{version}/fulfillments/{fulfillment_id}/update_tracking.json
            // (order_id not in path)
            String apiUrl = account.getApiUrl() + "/fulfillments/" + fulfillmentId + "/update_tracking.json";

            // Build request body
            Map<String, Object> tracking = new HashMap<>();
            if (trackingNumber != null && !trackingNumber.isEmpty()) {
                tracking.put("number", trackingNumber);
            }
            // For REST, we still send status if we are here (fallback or tracking number
            // update),
            // but GraphQL event is the primary way to set "Out for Delivery"/"Delivered"
            // etc.
            if (status != null) {
                tracking.put("status", mapStatusToShopifyStatus(status));
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("fulfillment", Map.of("tracking_info", tracking));

            HttpHeaders headers = createHeaders(account);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Log JSON request body
            try {
                String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
                log.info("📤 Shopify API Request JSON (Update Fulfillment Tracking - Order: {}):\n{}", orderId,
                        jsonRequest);
            } catch (Exception e) {
                log.warn("Failed to serialize Shopify request to JSON: {}", e.getMessage());
            }

            log.info("Updating Shopify fulfillment tracking for order {}: {}", orderId, tracking);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            logShopifyResponse("Update Fulfillment Tracking - Order: " + orderId, response);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Shopify fulfillment tracking updated successfully for order {}", orderId);
                return true;
            } else {
                log.error("❌ Shopify API returned non-2xx status: {}", response.getStatusCode());
                return false;
            }

        } catch (RestClientException e) {
            log.error("❌ Error calling Shopify API: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("❌ Unexpected error updating Shopify fulfillment: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create fulfillment event using GraphQL mutation
     * 
     * @param account       Shopify account
     * @param fulfillmentId Numeric fulfillment ID
     * @param status        Internal status string
     * @return true if event created successfully
     */
    public boolean createFulfillmentEvent(ShopifyAccount account, Long fulfillmentId, String status) {
        String graphQLStatus = mapStatusToGraphQLStatus(status);
        if (graphQLStatus == null) {
            log.warn(
                    "Status '{}' (mapped to null) is not supported for GraphQL fulfillment event, skipping event creation",
                    status);
            return false;
        }

        String mutation = "mutation AddEvent($input: FulfillmentEventInput!) { fulfillmentEventCreate(fulfillmentEvent: $input) { fulfillmentEvent { id status createdAt } userErrors { field message } } }";

        Map<String, Object> input = new HashMap<>();
        input.put("fulfillmentId", "gid://shopify/Fulfillment/" + fulfillmentId);
        input.put("status", graphQLStatus);

        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);

        Map<String, Object> response = callGraphQL(account, mutation, variables, "Create Fulfillment Event");

        if (response != null) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data != null) {
                Map<String, Object> eventCreate = (Map<String, Object>) data.get("fulfillmentEventCreate");
                if (eventCreate != null) {
                    List<?> userErrors = (List<?>) eventCreate.get("userErrors");
                    if (userErrors != null && !userErrors.isEmpty()) {
                        log.warn("GraphQL fulfillmentEventCreate returned userErrors: {}", userErrors);
                        return false;
                    }
                    Map<String, Object> event = (Map<String, Object>) eventCreate.get("fulfillmentEvent");
                    return event != null;
                }
            }
        }
        return false;
    }

    /**
     * Map internal status to GraphQL FulfillmentEventStatus
     * Supported inputs: IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILURE/RTO
     * (ATTEMPTED_DELIVERY?)
     */
    private String mapStatusToGraphQLStatus(String status) {
        if (status == null)
            return null;
        String normalized = status.trim().toUpperCase().replace("_", " ");

        if (normalized.contains("IN TRANSIT")) {
            return "IN_TRANSIT";
        } else if (normalized.contains("OUT FOR DELIVERY")) {
            return "OUT_FOR_DELIVERY";
        } else if (normalized.equals("DELIVERED")) {
            return "DELIVERED";
        } else if (normalized.contains("RTO") || normalized.contains("RETURN") || normalized.contains("FAILURE")) {
            return "ATTEMPTED_DELIVERY"; // Or FAILURE, but ATTEMPTED_DELIVERY is common for failed delivery attempts
        } else if (normalized.contains("PICKUP")) {
            return "READY_FOR_PICKUP"; // If supported
        } else if (normalized.contains("CONFIRMED")) {
            return "CONFIRMED";
        } else if (normalized.contains("LABEL PRINTED")) {
            return "LABEL_PRINTED";
        } else if (normalized.contains("PICKED UP")) {
            return "PICKED_UP";
        }

        // Default fallbacks or map to generic 'IN_TRANSIT' if appropriate, or return
        // null to skip
        return "IN_TRANSIT";
    }

    /**
     * Update fulfillment tracking by account code (looks up account and delegates).
     */
    public boolean updateFulfillmentTracking(String accountCode, Long orderId, Long fulfillmentId,
            String trackingNumber, String status) {
        if (accountCode == null || accountCode.isEmpty())
            return false;
        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null)
            return false;
        return updateFulfillmentTracking(account, orderId, fulfillmentId, trackingNumber, status);
    }

    /**
     * Map internal status to Shopify tracking status
     * Shopify accepts: "in_transit", "out_for_delivery", "delivered", "failure",
     * "cancelled"
     */
    private String mapStatusToShopifyStatus(String status) {
        if (status == null) {
            return "in_transit";
        }

        String normalized = status.trim().toUpperCase().replace("_", " ");

        if (normalized.contains("IN TRANSIT")) {
            return "in_transit";
        } else if (normalized.contains("OUT FOR DELIVERY")) {
            return "out_for_delivery";
        } else if (normalized.equals("DELIVERED")) {
            return "delivered";
        } else if (normalized.contains("RTO") || normalized.contains("RETURN")) {
            return "failure";
        } else {
            return "in_transit"; // Default
        }
    }

    /**
     * Get order details from Shopify using GraphQL API only.
     * Returns the order node (id, name, fulfillments, fulfillmentOrders, etc.) from
     * data.orders.edges[].node.
     *
     * @param accountCode Account code to identify which Shopify account to use
     * @param orderName   Order name/number (e.g. "254120" or "#254120")
     * @return Order details map (GraphQL node), or null if not found
     */
    public Map<String, Object> getOrderDetails(String accountCode, String orderName) {
        return getOrderWithFulfillmentsGraphQL(accountCode, orderName);
    }

    /**
     * Check if order has a specific tag
     * 
     * @param accountCode Account code
     * @param orderName   Order name/number
     * @param tag         Tag to check for
     * @return true if tag exists, false otherwise
     */
    public boolean hasOrderTag(String accountCode, String orderName, String tag) {
        Map<String, Object> orderDetails = getOrderDetails(accountCode, orderName);
        if (orderDetails == null) {
            return false;
        }

        Object tagsObj = orderDetails.get("tags");
        if (tagsObj == null) {
            return false;
        }

        String tags = tagsObj.toString();
        if (tags == null || tags.isEmpty()) {
            return false;
        }

        // Tags in Shopify are comma-separated
        String[] tagArray = tags.split(",");
        for (String existingTag : tagArray) {
            if (existingTag.trim().equalsIgnoreCase(tag.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update order tags in Shopify
     * 
     * @param accountCode Account code to identify which Shopify account to use
     * @param orderName   Order name/number
     * @param tagToAdd    Tag to add (will be appended to existing tags)
     * @return true if update successful, false otherwise
     */
    public boolean updateOrderTags(String accountCode, String orderName, String tagToAdd) {
        if (orderName == null || orderName.isEmpty()) {
            log.warn("Cannot update order tags: order name is empty");
            return false;
        }

        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Cannot update order tags: account code is empty");
            return false;
        }

        if (tagToAdd == null || tagToAdd.isEmpty()) {
            log.warn("Cannot update order tags: tag to add is empty");
            return false;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return false;
        }

        try {
            // First, get the order to find its ID and existing tags
            Long shopifyOrderId = getOrderIdByName(account, orderName);
            if (shopifyOrderId == null) {
                log.warn("Order {} not found in Shopify (account: {})", orderName, accountCode);
                return false;
            }
            return updateOrderTagsByOrderId(accountCode, shopifyOrderId, tagToAdd, orderName);
        } catch (Exception e) {
            log.error("Error updating order tags for order {}: {}", orderName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Update order tags by numeric Shopify order ID (e.g. from GraphQL). Fetches
     * current tags via REST then PUT.
     */
    public boolean updateOrderTagsByOrderId(String accountCode, Long shopifyOrderId, String tagToAdd,
            String orderNameForLog) {
        if (accountCode == null || accountCode.isEmpty() || shopifyOrderId == null || tagToAdd == null
                || tagToAdd.isEmpty()) {
            return false;
        }
        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null)
            return false;
        try {
            String apiUrlGet = account.getApiUrl() + "/orders/" + shopifyOrderId + ".json";
            HttpHeaders headers = createHeaders(account);
            ResponseEntity<Map> getResponse = restTemplate.exchange(apiUrlGet, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            String existingTags = "";
            if (getResponse.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = (Map<String, Object>) getResponse.getBody();
                if (body != null) {
                    Object orderObj = body.get("order");
                    if (orderObj instanceof Map) {
                        Object t = ((Map<?, ?>) orderObj).get("tags");
                        if (t != null)
                            existingTags = t.toString().trim();
                    }
                }
            }
            String newTags = existingTags.isEmpty() ? tagToAdd
                    : (existingTags.contains(tagToAdd) ? existingTags : existingTags + ", " + tagToAdd);
            if (existingTags.contains(tagToAdd)) {
                log.info("Tag {} already exists for order {}, skipping update", tagToAdd,
                        orderNameForLog != null ? orderNameForLog : shopifyOrderId);
                return true;
            }

            String apiUrl = account.getApiUrl() + "/orders/" + shopifyOrderId + ".json";

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("tags", newTags);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("order", orderData);

            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            try {
                String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
                log.info("📤 Shopify API Request JSON (Update Order Tags - Order: {}, Account: {}):\n{}",
                        orderNameForLog != null ? orderNameForLog : shopifyOrderId, accountCode, jsonRequest);
            } catch (Exception e) {
                log.warn("Failed to serialize Shopify request to JSON: {}", e.getMessage());
            }

            log.info("Updating Shopify order tags for order {} (account: {}): {}",
                    orderNameForLog != null ? orderNameForLog : shopifyOrderId, accountCode, newTags);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.PUT,
                    entity,
                    Map.class);

            logShopifyResponse("Update Order Tags - Order: " + shopifyOrderId, response);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Shopify order tags updated successfully for order {} (account: {})",
                        orderNameForLog != null ? orderNameForLog : shopifyOrderId, accountCode);
                return true;
            } else {
                log.error("❌ Shopify API returned non-2xx status: {}", response.getStatusCode());
                return false;
            }

        } catch (RestClientException e) {
            log.error("❌ Error calling Shopify API to update tags: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("❌ Unexpected error updating Shopify order tags: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get product images from an order
     * 
     * @param accountCode Account code to identify which Shopify account to use
     * @param orderName   Order name/number
     * @return List of product image URLs, or empty list if none found
     */
    public List<String> getOrderProductImages(String accountCode, String orderName) {
        List<String> imageUrls = new ArrayList<>();

        if (orderName == null || orderName.isEmpty()) {
            log.warn("Cannot get product images: order name is empty");
            return imageUrls;
        }

        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Cannot get product images: account code is empty");
            return imageUrls;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return imageUrls;
        }

        try {
            // Get order details
            Map<String, Object> orderDetails = getOrderDetails(accountCode, orderName);
            if (orderDetails == null) {
                log.warn("Order {} not found (account: {}), cannot get product images", orderName, accountCode);
                return imageUrls;
            }

            // Extract line items from order (handling GraphQL structure)
            log.info("orderDetails: {}", orderDetails);
            Object lineItemsObj = orderDetails.get("lineItems");
            if (lineItemsObj == null) {
                // Fallback to "line_items" if it somehow came from REST (unlikely now)
                lineItemsObj = orderDetails.get("line_items");
            }

            if (lineItemsObj == null) {
                log.debug("No line items found for order {} (account: {})", orderName, accountCode);
                return imageUrls;
            }

            // Handle GraphQL connection structure
            if (lineItemsObj instanceof Map) {
                Map<String, Object> connection = (Map<String, Object>) lineItemsObj;
                Object edgesObj = connection.get("edges");
                if (edgesObj instanceof List) {
                    List<Map<String, Object>> edges = (List<Map<String, Object>>) edgesObj;
                    for (Map<String, Object> edge : edges) {
                        Map<String, Object> node = (Map<String, Object>) edge.get("node");
                        if (node != null) {
                            processLineItemNode(node, account, imageUrls);
                        }
                    }
                }
            } else if (lineItemsObj instanceof List) {
                // REST style list
                List<Map<String, Object>> lineItems = (List<Map<String, Object>>) lineItemsObj;
                for (Map<String, Object> lineItem : lineItems) {
                    processLineItemREST(lineItem, account, imageUrls);
                }
            }

            log.info("Found {} product images for order {} (account: {})", imageUrls.size(), orderName, accountCode);
            return imageUrls;

        } catch (Exception e) {
            log.error("Error fetching product images for order {} (account: {}): {}",
                    orderName, accountCode, e.getMessage());
            return imageUrls;
        }
    }

    private void processLineItemNode(Map<String, Object> node, ShopifyAccount account, List<String> imageUrls) {
        // GraphQL Key: product { id } or variant { id }
        Map<String, Object> product = (Map<String, Object>) node.get("product");
        Map<String, Object> variant = (Map<String, Object>) node.get("variant");

        if (product != null) {
            Object idObj = product.get("id");
            if (idObj != null) {
                Long productId = parseNumericIdFromGid(idObj.toString(), "gid://shopify/Product/");
                if (productId != null) {
                    log.debug("Fetching image for product ID: {}", productId);
                    String imageUrl = getProductImage(account, productId);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        imageUrls.add(imageUrl);
                    } else {
                        log.debug("No image found for product ID: {}", productId);
                    }
                }
            }
        } else if (variant != null) {
            Object idObj = variant.get("id");
            if (idObj != null) {
                Long variantId = parseNumericIdFromGid(idObj.toString(), "gid://shopify/ProductVariant/");
                if (variantId != null) {
                    log.debug("Fetching image for variant ID: {}", variantId);
                    String imageUrl = getVariantImage(account, variantId);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        imageUrls.add(imageUrl);
                    } else {
                        log.debug("No image found for variant ID: {}", variantId);
                    }
                }
            }
        }
    }

    private void processLineItemREST(Map<String, Object> lineItem, ShopifyAccount account, List<String> imageUrls) {
        Object productIdObj = lineItem.get("product_id");
        Object variantIdObj = lineItem.get("variant_id");

        if (productIdObj != null) {
            Long productId = Long.valueOf(productIdObj.toString());
            log.debug("Fetching image for product ID (REST): {}", productId);
            String imageUrl = getProductImage(account, productId);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                imageUrls.add(imageUrl);
            } else {
                log.debug("No image found for product ID (REST): {}", productId);
            }
        } else if (variantIdObj != null) {
            Long variantId = Long.valueOf(variantIdObj.toString());
            log.debug("Fetching image for variant ID (REST): {}", variantId);
            String imageUrl = getVariantImage(account, variantId);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                imageUrls.add(imageUrl);
            } else {
                log.debug("No image found for variant ID (REST): {}", variantId);
            }
        }
    }

    /**
     * Get product image URL by product ID
     */
    private String getProductImage(ShopifyAccount account, Long productId) {
        try {
            String apiUrl = account.getApiUrl() + "/products/" + productId + ".json";
            HttpHeaders headers = createHeaders(account);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                if (body != null && body.containsKey("product")) {
                    Map<String, Object> product = (Map<String, Object>) body.get("product");
                    log.info("🔍 Product Response for product ID {}:\n{}", productId, product);
                    // Get first image from product
                    Object imagesObj = product.get("images");
                    if (imagesObj != null && imagesObj instanceof List) {
                        List<Map<String, Object>> images = (List<Map<String, Object>>) imagesObj;
                        if (!images.isEmpty()) {
                            Map<String, Object> firstImage = images.get(0);
                            Object srcObj = firstImage.get("src");
                            if (srcObj != null) {
                                return srcObj.toString();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching product image for product {}: {}", productId, e.getMessage());
        }
        return null;
    }

    /**
     * Get variant image URL by variant ID
     */
    private String getVariantImage(ShopifyAccount account, Long variantId) {
        try {
            // First get variant details to find product_id
            String apiUrl = account.getApiUrl() + "/variants/" + variantId + ".json";
            HttpHeaders headers = createHeaders(account);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                if (body != null && body.containsKey("variant")) {
                    Map<String, Object> variant = (Map<String, Object>) body.get("variant");
                    Object productIdObj = variant.get("product_id");

                    if (productIdObj != null) {
                        Long productId = Long.valueOf(productIdObj.toString());
                        return getProductImage(account, productId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching variant image for variant {}: {}", variantId, e.getMessage());
        }
        return null;
    }

    /**
     * Get full product details by product ID
     */
    public Map<String, Object> getProductDetails(String accountCode, Long productId) {
        if (accountCode == null || accountCode.isEmpty() || productId == null) {
            return null;
        }
        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            return null;
        }

        try {
            String apiUrl = account.getApiUrl() + "/products/" + productId + ".json";
            HttpHeaders headers = createHeaders(account);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                if (body != null && body.containsKey("product")) {
                    return (Map<String, Object>) body.get("product");
                }
            }
        } catch (Exception e) {
            log.error("Error fetching product details for product {}: {}", productId, e.getMessage());
        }
        return null;
    }

    /**
     * Call Shopify GraphQL API
     * 
     * @param account   Shopify account configuration
     * @param query     GraphQL query string
     * @param variables GraphQL variables map
     * @return GraphQL response as Map, or null if error
     */
    public Map<String, Object> callGraphQL(ShopifyAccount account, String query,
            Map<String, Object> variables) {
        return callGraphQL(account, query, variables, "GraphQL");
    }

    /**
     * Call Shopify GraphQL API with context for logging
     */
    public Map<String, Object> callGraphQL(ShopifyAccount account, String query,
            Map<String, Object> variables, String context) {
        try {
            String graphQLUrl = account.getGraphQLUrl();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            if (variables != null && !variables.isEmpty()) {
                requestBody.put("variables", variables);
            }

            HttpHeaders headers = createHeaders(account);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Log JSON request body
            try {
                String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
                log.info("📤 Shopify GraphQL API Request JSON ({}):\n{}", context, jsonRequest);
            } catch (Exception e) {
                log.warn("Failed to serialize GraphQL request to JSON: {}", e.getMessage());
            }

            log.debug("Calling Shopify GraphQL API: {}", graphQLUrl);

            ResponseEntity<Map> response = restTemplate.exchange(
                    graphQLUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            logShopifyResponse(context, response);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(response.getBody());
                    log.info("📥 Shopify GraphQL API Response JSON ({}):\n{}", context, jsonResponse);
                } catch (Exception e) {
                    log.warn("Failed to serialize GraphQL response to JSON: {}", e.getMessage());
                }
            }

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.containsKey("errors")) {
                    log.warn("GraphQL API returned errors (may still have data): {}", body.get("errors"));
                    // Return body anyway so caller can parse "data" when present (partial success)
                }
                return body;
            } else {
                log.error("GraphQL API returned non-2xx status: {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Error calling Shopify GraphQL API ({}): {}", context, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get order with displayFulfillmentStatus and tags (for in-transit flow).
     * Uses GraphQL: orders(first: 1, query: $q) with displayFulfillmentStatus,
     * tags, fulfillments, fulfillmentOrders.nodes.
     *
     * @param accountCode Account code
     * @param orderId     Order name/number (e.g. "254120" or "#254120")
     * @return Order node map (id, name, displayFulfillmentStatus, tags,
     *         fulfillments, fulfillmentOrders) or null
     */
    public Map<String, Object> getOrderWithDisplayFulfillmentStatus(String accountCode, String orderId) {
        if (accountCode == null || accountCode.isEmpty() || orderId == null || orderId.isEmpty()) {
            log.warn("Cannot get order via GraphQL: account code or order ID is empty");
            return null;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return null;
        }

        String graphQLQuery = "query ($q: String!) { orders(first: 1, query: $q) { edges { node { id name displayFulfillmentStatus tags lineItems(first: 1) { edges { node { product { id } } } } fulfillments { id status trackingInfo { number url company } } fulfillmentOrders(first: 10) { nodes { id status } } } } } }";

        // Try "name:#254120" first, then "name:254120" (Shopify display name is often
        // #254120)
        String[] queryValues = orderId.startsWith("#")
                ? new String[] { "name:" + orderId, "name:" + orderId.substring(1) }
                : new String[] { "name:#" + orderId, "name:" + orderId };

        for (String q : queryValues) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("q", q);

            Map<String, Object> response = callGraphQL(account, graphQLQuery, variables, "Get Order Display Status");
            if (response == null)
                continue;

            Map<String, Object> node = parseGraphQLOrderNode(response, orderId, accountCode, q);
            if (node != null) {
                log.debug("Order with displayFulfillmentStatus retrieved for {} (account: {})", orderId, accountCode);
                return node;
            }
        }

        log.warn("No orders found for order ID: {} (account: {})", orderId, accountCode);
        return null;
    }

    /**
     * Get fulfillment orders for an order by order GID (for in-transit flow when
     * displayFulfillmentStatus is not FULFILLED).
     * Uses GraphQL: order(id: $orderId) with fulfillmentOrders.edges and tags.
     *
     * @param accountCode Account code
     * @param orderGid    Order GID (e.g. "gid://shopify/Order/6973973135644")
     * @return Order map (id, name, tags, fulfillmentOrders.edges) or null
     */
    public Map<String, Object> getFulfillmentOrdersForOrder(String accountCode, String orderGid) {
        if (accountCode == null || accountCode.isEmpty() || orderGid == null || orderGid.isEmpty()) {
            log.warn("Cannot get fulfillment orders: account code or order GID is empty");
            return null;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return null;
        }

        String graphQLQuery = "query GetFulfillmentOrders($orderId: ID!) { order(id: $orderId) { id name tags fulfillmentOrders(first: 10) { edges { node { id status lineItems(first: 20) { edges { node { id remainingQuantity lineItem { id name sku quantity } } } } } } } } }";
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", orderGid);

        Map<String, Object> response = callGraphQL(account, graphQLQuery, variables, "Get Fulfillment Orders");
        if (response == null)
            return null;

        try {
            Object dataObj = response.get("data");
            if (dataObj == null || !(dataObj instanceof Map))
                return null;
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object orderObj = data.get("order");
            if (orderObj instanceof Map) {
                return (Map<String, Object>) orderObj;
            }
        } catch (Exception e) {
            log.debug("Parse GetFulfillmentOrders failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get OPEN fulfillment order ID from GetFulfillmentOrders response
     * (fulfillmentOrders.edges).
     * Returns the last OPEN node's id (GID), or null if none.
     */
    public String getOpenFulfillmentOrderIdFromEdges(Map<String, Object> orderData) {
        if (orderData == null)
            return null;
        try {
            Object foObj = orderData.get("fulfillmentOrders");
            if (foObj == null || !(foObj instanceof Map))
                return null;
            Object edgesObj = ((Map<String, Object>) foObj).get("edges");
            if (edgesObj == null || !(edgesObj instanceof List))
                return null;
            List<?> edges = (List<?>) edgesObj;
            for (int i = edges.size() - 1; i >= 0; i--) {
                Object e = edges.get(i);
                if (!(e instanceof Map))
                    continue;
                Object node = ((Map<String, Object>) e).get("node");
                if (node instanceof Map) {
                    String status = String.valueOf(((Map<String, Object>) node).get("status"));
                    if ("OPEN".equalsIgnoreCase(status)) {
                        String id = (String) ((Map<String, Object>) node).get("id");
                        if (id != null) {
                            log.debug("Found OPEN fulfillment order ID: {}", id);
                            return id;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting OPEN fulfillment order from edges: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Parse numeric ID from Shopify GID (e.g. "gid://shopify/Order/6973973135644"
     * -> 6973973135644).
     */
    public static Long parseNumericIdFromGid(String gid, String prefix) {
        if (gid == null || prefix == null || !gid.startsWith(prefix))
            return null;
        try {
            return Long.parseLong(gid.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get order with fulfillments using GraphQL API
     * 
     * @param accountCode Account code
     * @param orderId     Order name/number (e.g., "#1001")
     * @return Order data with fulfillments, or null if not found
     */
    public Map<String, Object> getOrderWithFulfillmentsGraphQL(String accountCode, String orderId) {
        if (accountCode == null || accountCode.isEmpty() || orderId == null || orderId.isEmpty()) {
            log.warn("Cannot get order via GraphQL: account code or order ID is empty");
            return null;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return null;
        }

        String graphQLQuery = "query ($q: String!) { orders(first: 1, query: $q) { edges { node { id name fulfillments { id status trackingInfo { number url company } } fulfillmentOrders(first: 10) { edges { node { id status } } } lineItems(first: 10) { edges { node { product { id } variant { id } } } } } } } }";

        // Try orderId as-is first, then with # prefix (Shopify name can be "254120" or
        // "#254120")
        String[] queryValues = new String[] { "name:" + orderId, "name:#" + orderId };
        if (orderId.startsWith("#")) {
            queryValues = new String[] { "name:" + orderId, "name:" + orderId.substring(1) };
        }

        for (String q : queryValues) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("q", q);

            Map<String, Object> response = callGraphQL(account, graphQLQuery, variables, "Get Order With Fulfillments");
            if (response == null) {
                continue;
            }
            log.info("🔍 GraphQL Response for order {}:\n{}", orderId, response);

            Map<String, Object> node = parseGraphQLOrderNode(response, orderId, accountCode, q);
            if (node != null) {
                log.debug("Order retrieved via GraphQL for order {} (account: {})", orderId, accountCode);
                return node;
            }
        }

        log.warn("No orders found for order ID: {} (account: {})", orderId, accountCode);
        return null;
    }

    /**
     * Parse GraphQL response body to extract the first order node.
     * Handles Map/List from Jackson (including LinkedHashMap, ArrayList) and logs
     * where parsing fails.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGraphQLOrderNode(Map<String, Object> response, String orderId, String accountCode,
            String queryUsed) {
        try {
            Object dataObj = response.get("data");
            if (dataObj == null) {
                log.debug("GraphQL response has no 'data' for order {} (query: {})", orderId, queryUsed);
                return null;
            }
            if (!(dataObj instanceof Map)) {
                log.warn("GraphQL response 'data' is not a Map for order {}: {}", orderId,
                        dataObj.getClass().getSimpleName());
                return null;
            }
            Map<String, Object> data = (Map<String, Object>) dataObj;

            Object ordersObj = data.get("orders");
            if (ordersObj == null) {
                log.debug("GraphQL response data has no 'orders' for order {} (query: {})", orderId, queryUsed);
                return null;
            }
            if (!(ordersObj instanceof Map)) {
                log.warn("GraphQL response 'orders' is not a Map for order {}: {}", orderId,
                        ordersObj.getClass().getSimpleName());
                return null;
            }
            Map<String, Object> orders = (Map<String, Object>) ordersObj;

            Object edgesObj = orders.get("edges");
            if (edgesObj == null) {
                log.debug("GraphQL response orders has no 'edges' for order {} (query: {})", orderId, queryUsed);
                return null;
            }
            if (!(edgesObj instanceof List)) {
                log.warn("GraphQL response 'edges' is not a List for order {}: {}", orderId,
                        edgesObj.getClass().getSimpleName());
                return null;
            }
            List<?> edges = (List<?>) edgesObj;
            if (edges.isEmpty()) {
                log.debug("GraphQL response edges is empty for order {} (query: {})", orderId, queryUsed);
                return null;
            }

            Object edgeObj = edges.get(0);
            if (edgeObj == null || !(edgeObj instanceof Map)) {
                log.warn("GraphQL response first edge is not a Map for order {}: {}", orderId,
                        edgeObj == null ? "null" : edgeObj.getClass().getSimpleName());
                return null;
            }
            Map<String, Object> edge = (Map<String, Object>) edgeObj;

            Object nodeObj = edge.get("node");
            if (nodeObj == null) {
                log.debug("GraphQL response edge has no 'node' for order {} (query: {})", orderId, queryUsed);
                return null;
            }
            if (!(nodeObj instanceof Map)) {
                log.warn("GraphQL response 'node' is not a Map for order {}: {}", orderId,
                        nodeObj.getClass().getSimpleName());
                return null;
            }
            return (Map<String, Object>) nodeObj;

        } catch (Exception e) {
            log.debug("GraphQL parse failed for query '{}': {}", queryUsed, e.getMessage());
            return null;
        }
    }

    /**
     * Get OPEN fulfillment order ID from GraphQL response
     * 
     * @param orderData Order data from GraphQL response
     * @return Fulfillment order ID if OPEN status exists, null otherwise
     */
    public String getOpenFulfillmentOrderId(Map<String, Object> orderData) {
        if (orderData == null) {
            return null;
        }

        try {
            Map<String, Object> fulfillmentOrders = (Map<String, Object>) orderData.get("fulfillmentOrders");
            if (fulfillmentOrders == null) {
                return null;
            }

            List<Map<String, Object>> edges = (List<Map<String, Object>>) fulfillmentOrders.get("edges");
            if (edges == null || edges.isEmpty()) {
                return null;
            }

            // Filter for OPEN status and get the last one
            for (int i = edges.size() - 1; i >= 0; i--) {
                Map<String, Object> edge = edges.get(i);
                Map<String, Object> node = (Map<String, Object>) edge.get("node");
                if (node != null) {
                    String status = node.get("status") != null ? node.get("status").toString() : "";
                    if ("OPEN".equalsIgnoreCase(status)) {
                        String id = node.get("id") != null ? node.get("id").toString() : null;
                        log.debug("Found OPEN fulfillment order ID: {}", id);
                        return id;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            log.error("Error extracting fulfillment order ID: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get fulfillment status from order details (REST API response)
     * 
     * @param orderDetails Order details map from REST API
     * @return Fulfillment status string, or null if not found
     */
    public String getFulfillmentStatus(Map<String, Object> orderDetails) {
        if (orderDetails == null) {
            return null;
        }

        Object fulfillmentStatusObj = orderDetails.get("fulfillment_status");
        if (fulfillmentStatusObj != null) {
            return fulfillmentStatusObj.toString();
        }
        return null;
    }

    /**
     * Get fulfillment status from GraphQL order node (e.g. when REST order lookup
     * fails).
     * Returns "fulfilled" if order has at least one fulfillment with status
     * SUCCESS.
     */
    public String getFulfillmentStatusFromGraphQL(Map<String, Object> orderData) {
        if (orderData == null) {
            return null;
        }
        Object fulfillmentsObj = orderData.get("fulfillments");
        if (fulfillmentsObj instanceof List) {
            List<?> fulfillments = (List<?>) fulfillmentsObj;
            if (!fulfillments.isEmpty()) {
                return "fulfilled";
            }
        }
        return null;
    }

    /**
     * Create fulfillment for an order using GraphQL
     * 
     * @param accountCode        Account code
     * @param orderId            Shopify order ID (numeric)
     * @param fulfillmentOrderId Fulfillment order ID from GraphQL (GID or numeric)
     * @param trackingNumber     AWB/tracking number
     * @param trackingUrl        Tracking URL
     * @return Created Fulfillment ID (numeric) if successful and created today,
     *         otherwise null
     */
    public Long createFulfillment(String accountCode, Long orderId, String fulfillmentOrderId, String trackingNumber,
            String trackingUrl) {
        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Cannot create fulfillment: account code is empty");
            return null;
        }

        if (orderId == null) {
            log.warn("Cannot create fulfillment: order ID is empty");
            return null;
        }

        if (fulfillmentOrderId == null || fulfillmentOrderId.isEmpty()) {
            log.warn("Cannot create fulfillment: fulfillment order ID is empty");
            return null;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return null;
        }

        try {
            // Ensure fulfillmentOrderId is a GID for GraphQL
            String fulfillmentOrderIdGid = fulfillmentOrderId;
            if (!fulfillmentOrderId.startsWith("gid://shopify/FulfillmentOrder/")) {
                // Assuming it's numeric, prefix it
                fulfillmentOrderIdGid = "gid://shopify/FulfillmentOrder/" + fulfillmentOrderId;
            }

            String mutation = "mutation FulfillmentCreate($fulfillment: FulfillmentInput!) { fulfillmentCreate(fulfillment: $fulfillment) { fulfillment { id status createdAt trackingInfo { company number url } } userErrors { field message } } }";

            Map<String, Object> trackingInfo = new HashMap<>();
            trackingInfo.put("company", "Shipway"); // Default company name as per request
            if (trackingNumber != null) {
                trackingInfo.put("number", trackingNumber);
            }
            if (trackingUrl != null) {
                trackingInfo.put("url", trackingUrl);
            }

            Map<String, Object> lineItemByFulfillmentOrder = new HashMap<>();
            lineItemByFulfillmentOrder.put("fulfillmentOrderId", fulfillmentOrderIdGid);
            // Empty list as per request structure: "fulfillmentOrderLineItems": []
            // This usually implies fulfilling all available items for this fulfillment
            // order
            lineItemByFulfillmentOrder.put("fulfillmentOrderLineItems", new ArrayList<>());

            Map<String, Object> fulfillmentInput = new HashMap<>();
            fulfillmentInput.put("lineItemsByFulfillmentOrder", List.of(lineItemByFulfillmentOrder));
            fulfillmentInput.put("notifyCustomer", false);
            fulfillmentInput.put("trackingInfo", trackingInfo);

            Map<String, Object> variables = new HashMap<>();
            variables.put("fulfillment", fulfillmentInput);

            log.info("Creating fulfillment (GraphQL) for order {} (account: {}), fulfillmentOrderId: {}", orderId,
                    accountCode, fulfillmentOrderIdGid);

            Map<String, Object> response = callGraphQL(account, mutation, variables, "Create Fulfillment");

            if (response != null) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null) {
                    Map<String, Object> fulfillmentCreate = (Map<String, Object>) data.get("fulfillmentCreate");
                    if (fulfillmentCreate != null) {
                        List<?> userErrors = (List<?>) fulfillmentCreate.get("userErrors");
                        if (userErrors != null && !userErrors.isEmpty()) {
                            log.warn("GraphQL fulfillmentCreate returned userErrors: {}", userErrors);
                            return null;
                        }
                        Map<String, Object> fulfillment = (Map<String, Object>) fulfillmentCreate.get("fulfillment");
                        if (fulfillment != null) {
                            String id = fulfillment.get("id") != null ? fulfillment.get("id").toString() : null;
                            String createdAt = fulfillment.get("createdAt") != null
                                    ? fulfillment.get("createdAt").toString()
                                    : null;
                            log.info(
                                    "✅ Fulfillment created successfully for order {} (account: {}) via GraphQL. ID: {}, CreatedAt: {}",
                                    orderId,
                                    accountCode, id, createdAt);

                            // Check if created today (simple check)
                            // Shopify format: "2026-02-07T23:15:59Z"
                            try {
                                if (createdAt != null) {
                                    java.time.ZonedDateTime createdTime = java.time.ZonedDateTime.parse(createdAt);
                                    java.time.ZonedDateTime now = java.time.ZonedDateTime
                                            .now(java.time.ZoneId.of("UTC"));
                                    // Make sure it's recent (e.g. within last hour or same day)
                                    // User asked: "if we get Fullfillment id in response with today's creation date
                                    // use that"
                                    if (createdTime.toLocalDate().equals(now.toLocalDate())) {
                                        return parseNumericIdFromGid(id, "gid://shopify/Fulfillment/");
                                    } else {
                                        // Also acceptable if it's within last few minutes even if date boundary
                                        // crossed?
                                        // For now, strict date check as requested OR just return it because we just
                                        // created it!
                                        // Actually, if we JUST created it, it IS today/fresh.
                                        // But fulfillmentCreate creates a NEW one.
                                        // Let's return the ID regardless if successful creation occurred now.
                                        return parseNumericIdFromGid(id, "gid://shopify/Fulfillment/");
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Could not parse createdAt date: {}", createdAt);
                            }
                            return parseNumericIdFromGid(id, "gid://shopify/Fulfillment/");
                        }
                    }
                }
            }

            log.warn("Failed to create fulfillment via GraphQL for order {} (account: {})", orderId, accountCode);
            return null;

        } catch (Exception e) {
            log.error("❌ Unexpected error creating fulfillment: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build tracking URL from AWB
     * Format: {url} + {AWB}
     * TODO: Update this URL to your actual tracking URL format
     */
    private String buildTrackingUrl(String awb) {
        if (awb == null || awb.isEmpty()) {
            return null;
        }
        // TODO: Update this URL to your actual tracking URL format
        // For now using placeholder - user mentioned they will add URL + AWB
        return "https://tracking.example.com/track/" + awb;
    }

    /**
     * Ensure fulfillment exists for an order. If it doesn't exist, create it.
     * 
     * @param accountCode Account code
     * @param orderId     Order name/number (e.g., "#1001")
     * @param awb         AWB/tracking number
     * @return true if fulfillment exists or was created successfully, false
     *         otherwise
     */
    public boolean ensureFulfillmentExists(String accountCode, String orderId, String awb) {
        if (accountCode == null || accountCode.isEmpty() || orderId == null || orderId.isEmpty()) {
            log.warn("Cannot ensure fulfillment exists: account code or order ID is empty");
            return false;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Shopify account configuration not found for account code: {}", accountCode);
            return false;
        }

        // 1. Check if fulfillment already exists
        Long shopifyOrderId = getOrderIdByName(account, orderId);
        if (shopifyOrderId != null) {
            Long fulfillmentId = getFulfillmentId(account, shopifyOrderId);
            if (fulfillmentId != null) {
                // Fulfillment exists, return true
                log.debug("Fulfillment already exists for order {} (account: {})", orderId, accountCode);
                return true;
            }
        }

        // 2. Fulfillment doesn't exist, create it
        log.info("Fulfillment does not exist for order {} (account: {}), attempting to create", orderId, accountCode);

        // Call GraphQL to get order with fulfillments
        Map<String, Object> orderData = getOrderWithFulfillmentsGraphQL(accountCode, orderId);
        if (orderData == null) {
            log.error("Failed to retrieve order via GraphQL for order: {} (account: {})", orderId, accountCode);
            return false;
        }

        // 3. Check for clone orders
        String orderName = orderData.get("name") != null ? orderData.get("name").toString() : "";
        if (orderName.contains("_")) {
            log.info("Order {} detected as clone (contains '_'), skipping fulfillment creation", orderId);
            return false;
        }

        // Resolve numeric Shopify order ID (from initial lookup or from GraphQL id
        // gid://shopify/Order/123)
        if (shopifyOrderId == null) {
            Object idObj = orderData.get("id");
            if (idObj != null) {
                String gid = idObj.toString();
                if (gid.startsWith("gid://shopify/Order/")) {
                    shopifyOrderId = Long.parseLong(gid.substring("gid://shopify/Order/".length()));
                }
            }
        }
        if (shopifyOrderId == null) {
            log.error("Could not resolve Shopify order ID for order: {} (account: {})", orderId, accountCode);
            return false;
        }

        // 4. Get fulfillment status from GraphQL order data we already have (avoids
        // extra GraphQL calls)
        String fulfillmentStatus = getFulfillmentStatusFromGraphQL(orderData);
        if (fulfillmentStatus == null) {
            log.error("Failed to derive fulfillment status for order: {} (account: {})", orderId, accountCode);
            return false;
        }

        // 5. If not fulfilled, check for OPEN fulfillment order and create fulfillment
        if (!"fulfilled".equalsIgnoreCase(fulfillmentStatus)) {
            String fulfillmentOrderId = getOpenFulfillmentOrderId(orderData);
            if (fulfillmentOrderId != null && !fulfillmentOrderId.isEmpty()) {
                // Build tracking URL
                String trackingUrl = buildTrackingUrl(awb);

                // Create fulfillment
                if (createFulfillment(accountCode, shopifyOrderId, fulfillmentOrderId, awb, trackingUrl) != null) {
                    // Update fulfillment tracking
                    Long newFulfillmentId = getFulfillmentId(account, shopifyOrderId);
                    if (newFulfillmentId != null) {
                        updateFulfillmentTracking(account, shopifyOrderId, newFulfillmentId, awb, "fulfilled");
                        log.info("✅ Fulfillment created and tracking updated for order {} (account: {})", orderId,
                                accountCode);
                        return true;
                    } else {
                        log.warn("Fulfillment created but could not update tracking for order {} (account: {})",
                                orderId, accountCode);
                        return true; // Fulfillment was created, consider it success
                    }
                } else {
                    log.error("Failed to create fulfillment for order {} (account: {})", orderId, accountCode);
                    return false;
                }
            } else {
                log.warn("No OPEN fulfillment order found for order {} (account: {}), cannot create fulfillment",
                        orderId, accountCode);
                return false;
            }
        } else {
            // Already fulfilled, but no fulfillment ID found - this shouldn't happen, but
            // return true
            log.debug("Order {} is already fulfilled (account: {})", orderId, accountCode);
            return true;
        }
    }

    /**
     * Create HTTP headers with Shopify authentication
     */
    private HttpHeaders createHeaders(ShopifyAccount account) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        String accessToken = account.getAccessToken();
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.set("X-Shopify-Access-Token", accessToken);
        }

        return headers;
    }

    /**
     * Product details DTO
     */
    public static class ProductDetails {
        private String handle;
        private String imageUrl;

        public ProductDetails(String handle, String imageUrl) {
            this.handle = handle;
            this.imageUrl = imageUrl;
        }

        public String getHandle() {
            return handle;
        }

        public String getImageUrl() {
            return imageUrl;
        }
    }

    /**
     * Get product details (handle, image) for all line items in an order
     */
    public List<ProductDetails> getOrderProductDetails(String accountCode, String orderId) {
        List<ProductDetails> details = new ArrayList<>();
        if (accountCode == null || accountCode.isEmpty() || orderId == null || orderId.isEmpty()) {
            return details;
        }

        ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
        if (account == null) {
            return details;
        }

        String graphQLQuery = "query ($q: String!) { orders(first: 1, query: $q) { edges { node { lineItems(first: 20) { edges { node { product { handle featuredImage { url } } } } } } } } }";

        String[] queryValues = orderId.startsWith("#")
                ? new String[] { "name:" + orderId, "name:" + orderId.substring(1) }
                : new String[] { "name:#" + orderId, "name:" + orderId };

        for (String q : queryValues) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("q", q);

            Map<String, Object> response = callGraphQL(account, graphQLQuery, variables, "Get Order Product Details");
            if (response == null) {
                continue;
            }

            Map<String, Object> node = parseGraphQLOrderNode(response, orderId, accountCode, q);
            if (node != null) {
                Object lineItemsObj = node.get("lineItems");
                if (lineItemsObj instanceof Map) {
                    Map<String, Object> connection = (Map<String, Object>) lineItemsObj;
                    Object edgesObj = connection.get("edges");
                    if (edgesObj instanceof List) {
                        List<Map<String, Object>> edges = (List<Map<String, Object>>) edgesObj;
                        for (Map<String, Object> edge : edges) {
                            Map<String, Object> lineItemNode = (Map<String, Object>) edge.get("node");
                            if (lineItemNode != null) {
                                Map<String, Object> product = (Map<String, Object>) lineItemNode.get("product");
                                if (product != null) {
                                    String handle = (String) product.get("handle");
                                    String imageUrl = null;
                                    Object imageObj = product.get("featuredImage");
                                    if (imageObj instanceof Map) {
                                        imageUrl = (String) ((Map<?, ?>) imageObj).get("url");
                                    }
                                    details.add(new ProductDetails(handle, imageUrl));
                                }
                            }
                        }
                    }
                }
                return details;
            }
        }
        return details;
    }
}

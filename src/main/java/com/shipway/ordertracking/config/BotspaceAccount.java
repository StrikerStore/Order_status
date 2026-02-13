package com.shipway.ordertracking.config;

/**
 * Botspace account configuration with template IDs
 */
public class BotspaceAccount {
    private String url;
    private String key;
    private String endpoint;

    // Template IDs for different order statuses
    private String inTransitTemplateId;
    private String outForDeliveryTemplateId;
    private String deliveredTemplateId;
    private String orderCreatedTemplateId;
    private String shopifyFulfillmentTemplateId;
    private String abandonedCartTemplateId;

    public BotspaceAccount() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getInTransitTemplateId() {
        return inTransitTemplateId;
    }

    public void setInTransitTemplateId(String inTransitTemplateId) {
        this.inTransitTemplateId = inTransitTemplateId;
    }

    public String getOutForDeliveryTemplateId() {
        return outForDeliveryTemplateId;
    }

    public void setOutForDeliveryTemplateId(String outForDeliveryTemplateId) {
        this.outForDeliveryTemplateId = outForDeliveryTemplateId;
    }

    public String getDeliveredTemplateId() {
        return deliveredTemplateId;
    }

    public void setDeliveredTemplateId(String deliveredTemplateId) {
        this.deliveredTemplateId = deliveredTemplateId;
    }

    public String getOrderCreatedTemplateId() {
        return orderCreatedTemplateId;
    }

    public void setOrderCreatedTemplateId(String orderCreatedTemplateId) {
        this.orderCreatedTemplateId = orderCreatedTemplateId;
    }

    public String getShopifyFulfillmentTemplateId() {
        return shopifyFulfillmentTemplateId;
    }

    public void setShopifyFulfillmentTemplateId(String shopifyFulfillmentTemplateId) {
        this.shopifyFulfillmentTemplateId = shopifyFulfillmentTemplateId;
    }

    public String getAbandonedCartTemplateId() {
        return abandonedCartTemplateId;
    }

    public void setAbandonedCartTemplateId(String abandonedCartTemplateId) {
        this.abandonedCartTemplateId = abandonedCartTemplateId;
    }

    /**
     * Build the full API URL
     */
    public String getApiUrl() {
        String baseUrl = url != null ? url : "";
        String endpointPath = endpoint != null ? endpoint : "";

        // Remove trailing slash from baseUrl and leading slash from endpoint
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (endpointPath.startsWith("/")) {
            endpointPath = endpointPath.substring(1);
        }

        return baseUrl + "/" + endpointPath;
    }
}

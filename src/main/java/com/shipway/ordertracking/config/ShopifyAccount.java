package com.shipway.ordertracking.config;

public class ShopifyAccount {
    private String shop;
    private String accessToken;

    public ShopifyAccount() {
    }

    public String getShop() {
        return shop;
    }

    public void setShop(String shop) {
        this.shop = shop;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    private String trackingUrlTemplate;

    public String getTrackingUrlTemplate() {
        return trackingUrlTemplate;
    }

    public void setTrackingUrlTemplate(String trackingUrlTemplate) {
        this.trackingUrlTemplate = trackingUrlTemplate;
    }

    public String getApiUrl() {
        return ShopifyProperties.buildApiUrl(shop);
    }

    public String getGraphQLUrl() {
        return ShopifyProperties.buildGraphQLUrl(shop);
    }

    private String productUrl;

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }
}

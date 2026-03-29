package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FulfillAttemptResult {

    private String accountCode;
    private String orderId;
    private String orderTrackingStatus;
    /** Shopify update string: {@code delivered}, {@code in_transit}, or {@code out_for_delivery}. */
    private String shopifyShipmentStatus;
    private boolean success;
    private String message;

    public String getAccountCode() {
        return accountCode;
    }

    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderTrackingStatus() {
        return orderTrackingStatus;
    }

    public void setOrderTrackingStatus(String orderTrackingStatus) {
        this.orderTrackingStatus = orderTrackingStatus;
    }

    public String getShopifyShipmentStatus() {
        return shopifyShipmentStatus;
    }

    public void setShopifyShipmentStatus(String shopifyShipmentStatus) {
        this.shopifyShipmentStatus = shopifyShipmentStatus;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

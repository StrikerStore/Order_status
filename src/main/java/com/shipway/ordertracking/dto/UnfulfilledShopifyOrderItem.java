package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unfulfilled Shopify order with latest {@code order_tracking.shipment_status} when it matches the report filter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnfulfilledShopifyOrderItem {

    private String accountCode;
    /** {@code order_id} from {@code order_tracking} (latest row). */
    private String orderId;
    /** Shopify order {@code name} (e.g. {@code #1001}). */
    private String shopifyOrderName;
    /** Numeric id from Shopify Admin (REST id), parsed from GraphQL GID. */
    private String shopifyOrderNumericId;
    private String shopifyOrderGid;
    /** Raw {@code shipment_status} from {@code order_tracking}. */
    private String orderTrackingStatus;
    /** Shopify {@code displayFulfillmentStatus} (e.g. UNFULFILLED, PARTIALLY_FULFILLED). */
    private String shopifyDisplayFulfillmentStatus;
    /** Shopify GraphQL {@code createdAt} (ISO-8601). */
    private String shopifyCreatedAt;
    /** Latest {@code awb} from {@code label} for this order (if any). */
    private String awb;

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

    public String getShopifyOrderName() {
        return shopifyOrderName;
    }

    public void setShopifyOrderName(String shopifyOrderName) {
        this.shopifyOrderName = shopifyOrderName;
    }

    public String getShopifyOrderNumericId() {
        return shopifyOrderNumericId;
    }

    public void setShopifyOrderNumericId(String shopifyOrderNumericId) {
        this.shopifyOrderNumericId = shopifyOrderNumericId;
    }

    public String getShopifyOrderGid() {
        return shopifyOrderGid;
    }

    public void setShopifyOrderGid(String shopifyOrderGid) {
        this.shopifyOrderGid = shopifyOrderGid;
    }

    public String getOrderTrackingStatus() {
        return orderTrackingStatus;
    }

    public void setOrderTrackingStatus(String orderTrackingStatus) {
        this.orderTrackingStatus = orderTrackingStatus;
    }

    public String getShopifyDisplayFulfillmentStatus() {
        return shopifyDisplayFulfillmentStatus;
    }

    public void setShopifyDisplayFulfillmentStatus(String shopifyDisplayFulfillmentStatus) {
        this.shopifyDisplayFulfillmentStatus = shopifyDisplayFulfillmentStatus;
    }

    public String getShopifyCreatedAt() {
        return shopifyCreatedAt;
    }

    public void setShopifyCreatedAt(String shopifyCreatedAt) {
        this.shopifyCreatedAt = shopifyCreatedAt;
    }

    public String getAwb() {
        return awb;
    }

    public void setAwb(String awb) {
        this.awb = awb;
    }
}

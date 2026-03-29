package com.shipway.ordertracking.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response for GET preview: unfulfilled Shopify orders with matching {@code order_tracking} status.
 */
public class UnfulfilledShopifyPreviewResponse {

    private List<UnfulfilledShopifyOrderItem> items = new ArrayList<>();
    /** {@code order_id} values from {@code order_tracking} (same as {@code items[].orderId}), for quick copy/paste. */
    private List<String> orderIds = new ArrayList<>();
    private Map<String, Integer> counts = new HashMap<>();

    public List<UnfulfilledShopifyOrderItem> getItems() {
        return items;
    }

    public void setItems(List<UnfulfilledShopifyOrderItem> items) {
        this.items = items;
    }

    public List<String> getOrderIds() {
        return orderIds;
    }

    public void setOrderIds(List<String> orderIds) {
        this.orderIds = orderIds;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Integer> counts) {
        this.counts = counts;
    }
}

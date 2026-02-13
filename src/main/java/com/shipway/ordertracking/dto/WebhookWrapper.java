package com.shipway.ordertracking.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WebhookWrapper {

    private String timestamp;
    private String event;

    @JsonProperty("orders")
    private List<StatusUpdateWebhook> orders;

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public List<StatusUpdateWebhook> getOrders() {
        return orders;
    }

    public void setOrders(List<StatusUpdateWebhook> orders) {
        this.orders = orders;
    }
}

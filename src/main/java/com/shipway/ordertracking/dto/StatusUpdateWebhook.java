package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusUpdateWebhook {
    
    @JsonProperty("headers")
    private WebhookHeaders headers;
    
    @JsonProperty("params")
    private Object params;
    
    @JsonProperty("query")
    private Object query;
    
    @JsonProperty("body")
    @JsonDeserialize(using = WebhookBodyDeserializer.class)
    private WebhookBody body;
    
    @JsonProperty("webhookUrl")
    private String webhookUrl;
    
    @JsonProperty("executionMode")
    private String executionMode;

    public StatusUpdateWebhook() {
    }

    public WebhookHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(WebhookHeaders headers) {
        this.headers = headers;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }

    public Object getQuery() {
        return query;
    }

    public void setQuery(Object query) {
        this.query = query;
    }

    public WebhookBody getBody() {
        return body;
    }

    public void setBody(WebhookBody body) {
        this.body = body;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookHeaders {
        @JsonProperty("host")
        private String host;
        
        @JsonProperty("user-agent")
        private String userAgent;
        
        @JsonProperty("content-type")
        private String contentType;
        
        // Add other headers as needed

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookBody {
        @JsonProperty("timestamp")
        private String timestamp;
        
        @JsonProperty("event")
        private String event;
        
        @JsonProperty("orders")
        private List<OrderStatus> orders;

        public WebhookBody() {
        }

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

        public List<OrderStatus> getOrders() {
            return orders;
        }

        public void setOrders(List<OrderStatus> orders) {
            this.orders = orders;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderStatus {
        @JsonProperty("order_id")
        private String orderId;
        
        @JsonProperty("account_code")
        private String accountCode;
        
        @JsonProperty("carrier_id")
        private String carrierId;
        
        @JsonProperty("awb")
        private String awb;
        
        @JsonProperty("current_shipment_status")
        private String currentShipmentStatus;
        
        @JsonProperty("previous_status")
        private String previousStatus;
        
        @JsonProperty("shipping_phone")
        private String shippingPhone;
        
        @JsonProperty("shipping_firstname")
        private String shippingFirstname;
        
        @JsonProperty("shipping_lastname")
        private String shippingLastname;
        
        @JsonProperty("number_of_product")
        private Integer numberOfProduct;
        
        @JsonProperty("number_of_quantity")
        private String numberOfQuantity;
        
        @JsonProperty("latest_message_status")
        private String latestMessageStatus;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getAccountCode() {
            return accountCode;
        }

        public void setAccountCode(String accountCode) {
            this.accountCode = accountCode;
        }

        public String getCarrierId() {
            return carrierId;
        }

        public void setCarrierId(String carrierId) {
            this.carrierId = carrierId;
        }

        public String getAwb() {
            return awb;
        }

        public void setAwb(String awb) {
            this.awb = awb;
        }

        public String getCurrentShipmentStatus() {
            return currentShipmentStatus;
        }

        public void setCurrentShipmentStatus(String currentShipmentStatus) {
            this.currentShipmentStatus = currentShipmentStatus;
        }

        public String getPreviousStatus() {
            return previousStatus;
        }

        public void setPreviousStatus(String previousStatus) {
            this.previousStatus = previousStatus;
        }

        public String getShippingPhone() {
            return shippingPhone;
        }

        public void setShippingPhone(String shippingPhone) {
            this.shippingPhone = shippingPhone;
        }

        public String getShippingFirstname() {
            return shippingFirstname;
        }

        public void setShippingFirstname(String shippingFirstname) {
            this.shippingFirstname = shippingFirstname;
        }

        public String getShippingLastname() {
            return shippingLastname;
        }

        public void setShippingLastname(String shippingLastname) {
            this.shippingLastname = shippingLastname;
        }

        public Integer getNumberOfProduct() {
            return numberOfProduct;
        }

        public void setNumberOfProduct(Integer numberOfProduct) {
            this.numberOfProduct = numberOfProduct;
        }

        public String getNumberOfQuantity() {
            return numberOfQuantity;
        }

        public void setNumberOfQuantity(String numberOfQuantity) {
            this.numberOfQuantity = numberOfQuantity;
        }

        public String getLatestMessageStatus() {
            return latestMessageStatus;
        }

        public void setLatestMessageStatus(String latestMessageStatus) {
            this.latestMessageStatus = latestMessageStatus;
        }
    }
}

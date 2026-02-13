package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClaimioTrackingRequest {
    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("account_code")
    private String accountCode;

    @JsonProperty("message_status")
    private String messageStatus;

    public ClaimioTrackingRequest() {
    }

    public ClaimioTrackingRequest(String orderId, String accountCode, String messageStatus) {
        this.orderId = orderId;
        this.accountCode = accountCode;
        this.messageStatus = messageStatus;
    }

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

    public String getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(String messageStatus) {
        this.messageStatus = messageStatus;
    }
}

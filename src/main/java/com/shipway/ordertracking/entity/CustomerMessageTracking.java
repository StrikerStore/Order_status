package com.shipway.ordertracking.entity;

import jakarta.persistence.*;

/**
 * Entity mapping to the existing customer_message_tracking table.
 * Used for deduplication before sending Botspace notifications.
 */
@Entity
@Table(name = "customer_message_tracking")
public class CustomerMessageTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "message_status", nullable = false)
    private String messageStatus;

    public CustomerMessageTracking() {
    }

    public CustomerMessageTracking(String orderId, String accountCode, String messageStatus) {
        this.orderId = orderId;
        this.accountCode = accountCode;
        this.messageStatus = messageStatus;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

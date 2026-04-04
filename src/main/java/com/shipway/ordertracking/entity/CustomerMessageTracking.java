package com.shipway.ordertracking.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Entity mapping to the existing customer_message_tracking table.
 * Used for deduplication before sending Botspace notifications.
 * created_at is set when the record is inserted (for "yesterday" follow-up queries).
 * <p>Add column if missing: {@code ALTER TABLE customer_message_tracking ADD COLUMN brand_name VARCHAR(255) NULL AFTER account_code;}
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

    @Column(name = "brand_name", length = 255)
    private String brandName;

    @Column(name = "message_status", nullable = false)
    private String messageStatus;

    @Column(name = "created_at")
    private Instant createdAt;

    public CustomerMessageTracking() {
    }

    public CustomerMessageTracking(String orderId, String accountCode, String messageStatus) {
        this(orderId, accountCode, messageStatus, null);
    }

    public CustomerMessageTracking(String orderId, String accountCode, String messageStatus, String brandName) {
        this.orderId = orderId;
        this.accountCode = accountCode;
        this.messageStatus = messageStatus;
        this.brandName = brandName;
        this.createdAt = Instant.now();
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

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(String messageStatus) {
        this.messageStatus = messageStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

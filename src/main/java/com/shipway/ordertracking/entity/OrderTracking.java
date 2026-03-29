package com.shipway.ordertracking.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Maps to {@code order_tracking} (shipment status history per order).
 * Column definitions align with the live MySQL schema (validate mode).
 */
@Entity
@Table(name = "order_tracking")
public class OrderTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    /** DB: {@code enum('active','inactive')}; use {@code active} / {@code inactive} as stored values. */
    @Column(name = "order_type", nullable = false, columnDefinition = "enum('active','inactive')")
    private String orderType;

    @Column(name = "shipment_status", nullable = false, length = 100)
    private String shipmentStatus;

    @Column(name = "timestamp", nullable = false)
    private Instant statusTimestamp;

    @Column(name = "ndr_reason", length = 255)
    private String ndrReason;

    @Column(name = "account_code", nullable = false, length = 50)
    private String accountCode;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public OrderTracking() {
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

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getShipmentStatus() {
        return shipmentStatus;
    }

    public void setShipmentStatus(String shipmentStatus) {
        this.shipmentStatus = shipmentStatus;
    }

    public Instant getStatusTimestamp() {
        return statusTimestamp;
    }

    public void setStatusTimestamp(Instant statusTimestamp) {
        this.statusTimestamp = statusTimestamp;
    }

    public String getNdrReason() {
        return ndrReason;
    }

    public void setNdrReason(String ndrReason) {
        this.ndrReason = ndrReason;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.shipway.ordertracking.repository;

/**
 * Projection for query joining customer_info and customer_message_tracking
 * (order_id, shipping_phone, account_code).
 */
public interface OrderPhoneProjection {

    String getOrderId();

    String getShippingPhone();

    String getAccountCode();
}

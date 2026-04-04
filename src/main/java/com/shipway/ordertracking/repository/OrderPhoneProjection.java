package com.shipway.ordertracking.repository;

/**
 * Projection for query joining customer_info and customer_message_tracking
 * (order_id, shipping_phone, account_code, brand_name).
 */
public interface OrderPhoneProjection {

    String getOrderId();

    String getShippingPhone();

    String getAccountCode();

    /** May be null for older {@code customer_message_tracking} rows. */
    String getBrandName();
}

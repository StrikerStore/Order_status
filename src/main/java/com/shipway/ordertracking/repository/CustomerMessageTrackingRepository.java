package com.shipway.ordertracking.repository;

import com.shipway.ordertracking.entity.CustomerMessageTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface CustomerMessageTrackingRepository extends JpaRepository<CustomerMessageTracking, Integer>,
        JpaSpecificationExecutor<CustomerMessageTracking> {

    boolean existsByOrderIdAndAccountCodeAndMessageStatus(String orderId, String accountCode, String messageStatus);

    boolean existsByOrderIdAndAccountCodeAndMessageStatusIn(String orderId, String accountCode, List<String> messageStatuses);

    /** Find records by status and created_at in range (e.g. yesterday for post-delivered follow-up). */
    List<CustomerMessageTracking> findByMessageStatusAndCreatedAtBetween(String messageStatus, Instant start, Instant end);

    /**
     * Join customer_info and customer_message_tracking to get order_id, shipping_phone, account_code
     * for orders that received sent_delivered message yesterday (created_at date = DB yesterday).
     */
    @Query(value = "SELECT ci.order_id AS orderId, ci.shipping_phone AS shippingPhone, cmt.account_code AS accountCode " +
            "FROM customer_info ci " +
            "INNER JOIN customer_message_tracking cmt ON cmt.order_id = ci.order_id " +
            "WHERE cmt.message_status = 'sent_delivered' " +
            "AND DATE(cmt.created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)", nativeQuery = true)
    List<OrderPhoneProjection> findOrderIdAndPhoneForSentDeliveredYesterday();

    @Query("SELECT DISTINCT c.messageStatus FROM CustomerMessageTracking c WHERE c.messageStatus IS NOT NULL ORDER BY c.messageStatus")
    List<String> findDistinctMessageStatuses();
}

package com.shipway.ordertracking.repository;

import com.shipway.ordertracking.entity.OrderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderTrackingRepository extends JpaRepository<OrderTracking, Integer> {

    /**
     * Latest {@code order_tracking} row per normalized order id (ignores {@code #} in {@code order_id})
     * for the given account. Used after loading Shopify unfulfilled orders.
     */
    @Query(value = """
            SELECT ot.* FROM order_tracking ot
            INNER JOIN (
              SELECT REPLACE(TRIM(order_id), '#', '') AS norm_oid, MAX(id) AS mid
              FROM order_tracking
              WHERE UPPER(TRIM(account_code)) = UPPER(TRIM(:accountCode))
              AND REPLACE(TRIM(order_id), '#', '') IN (:normIds)
              GROUP BY REPLACE(TRIM(order_id), '#', '')
            ) z ON REPLACE(TRIM(ot.order_id), '#', '') = z.norm_oid AND ot.id = z.mid
            WHERE UPPER(TRIM(ot.account_code)) = UPPER(TRIM(:accountCode))
            """, nativeQuery = true)
    List<OrderTracking> findLatestByAccountAndNormalizedOrderIds(@Param("accountCode") String accountCode,
            @Param("normIds") List<String> normIds);
}

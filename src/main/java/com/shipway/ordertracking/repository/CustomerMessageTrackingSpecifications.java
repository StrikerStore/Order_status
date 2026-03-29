package com.shipway.ordertracking.repository;

import com.shipway.ordertracking.entity.CustomerMessageTracking;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Dynamic filters for browsing {@link CustomerMessageTracking} (read-only UI).
 */
public final class CustomerMessageTrackingSpecifications {

    private CustomerMessageTrackingSpecifications() {
    }

    public static Specification<CustomerMessageTracking> accountCodeEqualsIgnoreCase(String accountCode) {
        return (root, query, cb) -> {
            if (accountCode == null || accountCode.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("accountCode")), accountCode.trim().toLowerCase(Locale.ROOT));
        };
    }

    /**
     * Case-insensitive substring match on {@code order_id}; {@code %} and {@code _} in input are escaped for LIKE.
     */
    public static Specification<CustomerMessageTracking> orderIdContainsIgnoreCase(String orderId) {
        return (root, query, cb) -> {
            if (orderId == null || orderId.isBlank()) {
                return cb.conjunction();
            }
            String t = orderId.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            return cb.like(cb.lower(root.get("orderId")), "%" + t + "%", '\\');
        };
    }

    public static Specification<CustomerMessageTracking> messageStatusEqualsIgnoreCase(String messageStatus) {
        return (root, query, cb) -> {
            if (messageStatus == null || messageStatus.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("messageStatus")), messageStatus.trim().toLowerCase(Locale.ROOT));
        };
    }
}

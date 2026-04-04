package com.shipway.ordertracking.service;

import com.shipway.ordertracking.entity.CustomerMessageTracking;
import com.shipway.ordertracking.repository.CustomerMessageTrackingRepository;
import com.shipway.ordertracking.repository.OrderPhoneProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Service for checking and adding message status in the database.
 * {@link #hasAnyStatus} / {@link #hasStatus} and insert idempotency use {@code order_id} + {@code brand_name} only
 * ({@code account_code} is stored but not part of dedup). NULL/blank brand aligned with SQL COALESCE.
 */
@Service
public class CustomerMessageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(CustomerMessageTrackingService.class);

    @Autowired
    private CustomerMessageTrackingRepository repository;

    private static String normalizeBrandKey(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            return "";
        }
        return brandName.trim();
    }

    private static String normalizeAccountKey(String accountCode) {
        return accountCode != null ? accountCode.trim() : "";
    }

    /**
     * Whether any row exists for this order + brand (from request) + one of the statuses.
     */
    public boolean hasAnyStatus(String orderId, String brandName, List<String> messageStatuses) {
        if (orderId == null || orderId.isBlank() || messageStatuses == null || messageStatuses.isEmpty()) {
            return false;
        }
        String brand = normalizeBrandKey(brandName);
        return repository.countByOrderBrandAndMessageStatusIn(orderId, brand, messageStatuses) > 0;
    }

    public boolean hasStatus(String orderId, String brandName, String messageStatus) {
        if (orderId == null || orderId.isBlank() || messageStatus == null || messageStatus.isBlank()) {
            return false;
        }
        String brand = normalizeBrandKey(brandName);
        return repository.countByOrderBrandAndMessageStatus(orderId, brand, messageStatus) > 0;
    }

    @Transactional
    public boolean addStatus(String orderId, String accountCode, String messageStatus) {
        return addStatus(orderId, accountCode, messageStatus, null);
    }

    /**
     * Persists {@code account_code} and optional {@code brand_name}; duplicate check is order + brand + status only.
     */
    @Transactional
    public boolean addStatus(String orderId, String accountCode, String messageStatus, String brandName) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("Cannot add message status: order ID is empty");
            return false;
        }
        if (messageStatus == null || messageStatus.isEmpty()) {
            log.warn("Cannot add message status: message status is empty");
            return false;
        }

        String acct = normalizeAccountKey(accountCode);
        String brandKey = normalizeBrandKey(brandName);
        if (acct.isEmpty() && brandKey.isEmpty()) {
            log.warn("Cannot add message status: account_code and brand_name are both empty");
            return false;
        }

        try {
            if (repository.countByOrderBrandAndMessageStatus(orderId, brandKey, messageStatus) > 0) {
                log.debug("Message status {} already exists for order {} (brand key: {}), skipping insert",
                        messageStatus, orderId, brandKey.isEmpty() ? "—" : brandKey);
                return true;
            }

            String brandStored = brandKey.isEmpty() ? null : brandKey;
            CustomerMessageTracking record = new CustomerMessageTracking(orderId, acct, messageStatus, brandStored);
            repository.save(record);
            log.info("Added message status {} for order {} (account_code: {}, brand_name: {})", messageStatus, orderId,
                    acct.isEmpty() ? "—" : acct, brandStored != null ? brandStored : "—");
            return true;
        } catch (Exception e) {
            log.error("Failed to add message status {} for order {} (account: {}): {}",
                    messageStatus, orderId, acct, e.getMessage(), e);
            return false;
        }
    }

    private static final String SENT_DELIVERED = "sent_delivered";

    public List<CustomerMessageTracking> findSentDeliveredYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        ZoneId zone = ZoneId.systemDefault();
        Instant start = yesterday.atStartOfDay(zone).toInstant();
        Instant end = yesterday.plusDays(1).atStartOfDay(zone).toInstant();
        return repository.findByMessageStatusAndCreatedAtBetween(SENT_DELIVERED, start, end);
    }

    public List<OrderPhoneProjection> findOrderIdAndPhoneForSentDeliveredYesterday() {
        return repository.findOrderIdAndPhoneForSentDeliveredYesterday();
    }
}

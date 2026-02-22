package com.shipway.ordertracking.service;

import com.shipway.ordertracking.entity.CustomerMessageTracking;
import com.shipway.ordertracking.repository.CustomerMessageTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for checking and adding message status in the database.
 * Used for deduplication before sending Botspace messages.
 */
@Service
public class CustomerMessageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(CustomerMessageTrackingService.class);

    @Autowired
    private CustomerMessageTrackingRepository repository;

    /**
     * Check if the order already has any of the given message statuses in the database.
     * Used for deduplication (e.g. skip if sent_inTransit OR failed_inTransit exists).
     */
    public boolean hasAnyStatus(String orderId, String accountCode, List<String> messageStatuses) {
        if (orderId == null || accountCode == null || messageStatuses == null || messageStatuses.isEmpty()) {
            return false;
        }
        return repository.existsByOrderIdAndAccountCodeAndMessageStatusIn(orderId, accountCode, messageStatuses);
    }

    /**
     * Check if the order already has this message status in the database.
     */
    public boolean hasStatus(String orderId, String accountCode, String messageStatus) {
        if (orderId == null || accountCode == null || messageStatus == null) {
            return false;
        }
        return repository.existsByOrderIdAndAccountCodeAndMessageStatus(orderId, accountCode, messageStatus);
    }

    /**
     * Add message status for the order. Idempotent - does not fail if record already exists.
     *
     * @return true if added or already existed, false on error
     */
    @Transactional
    public boolean addStatus(String orderId, String accountCode, String messageStatus) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("Cannot add message status: order ID is empty");
            return false;
        }
        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Cannot add message status: account code is empty");
            return false;
        }
        if (messageStatus == null || messageStatus.isEmpty()) {
            log.warn("Cannot add message status: message status is empty");
            return false;
        }

        try {
            if (repository.existsByOrderIdAndAccountCodeAndMessageStatus(orderId, accountCode, messageStatus)) {
                log.debug("Message status {} already exists for order {} (account: {}), skipping insert",
                        messageStatus, orderId, accountCode);
                return true;
            }

            CustomerMessageTracking record = new CustomerMessageTracking(orderId, accountCode, messageStatus);
            repository.save(record);
            log.info("Added message status {} for order {} (account: {})", messageStatus, orderId, accountCode);
            return true;
        } catch (Exception e) {
            log.error("Failed to add message status {} for order {} (account: {}): {}",
                    messageStatus, orderId, accountCode, e.getMessage(), e);
            return false;
        }
    }
}

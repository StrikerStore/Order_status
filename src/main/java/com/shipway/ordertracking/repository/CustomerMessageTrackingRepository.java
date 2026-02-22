package com.shipway.ordertracking.repository;

import com.shipway.ordertracking.entity.CustomerMessageTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerMessageTrackingRepository extends JpaRepository<CustomerMessageTracking, Integer> {

    boolean existsByOrderIdAndAccountCodeAndMessageStatus(String orderId, String accountCode, String messageStatus);

    boolean existsByOrderIdAndAccountCodeAndMessageStatusIn(String orderId, String accountCode, List<String> messageStatuses);
}

package com.shipway.ordertracking.controller;

import com.shipway.ordertracking.entity.CustomerMessageTracking;
import com.shipway.ordertracking.repository.CustomerMessageTrackingRepository;
import com.shipway.ordertracking.repository.CustomerMessageTrackingSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Read-only UI for {@code customer_message_tracking} rows (filters + pagination).
 */
@Controller
@RequestMapping("/ui/message-tracking")
public class MessageTrackingUiController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private CustomerMessageTrackingRepository customerMessageTrackingRepository;

    @GetMapping
    public String index(
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String messageStatus,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size,
            Model model) {

        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        Specification<CustomerMessageTracking> spec = Specification
                .where(CustomerMessageTrackingSpecifications.accountCodeEqualsIgnoreCase(accountCode))
                .and(CustomerMessageTrackingSpecifications.orderIdContainsIgnoreCase(orderId))
                .and(CustomerMessageTrackingSpecifications.messageStatusEqualsIgnoreCase(messageStatus));

        Pageable pageable = PageRequest.of(safePage, cappedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<CustomerMessageTracking> result = customerMessageTrackingRepository.findAll(spec, pageable);

        List<String> statusOptions = customerMessageTrackingRepository.findDistinctMessageStatuses();

        model.addAttribute("page", result);
        model.addAttribute("filterAccountCode", accountCode != null ? accountCode : "");
        model.addAttribute("filterOrderId", orderId != null ? orderId : "");
        model.addAttribute("filterMessageStatus", messageStatus != null ? messageStatus : "");
        model.addAttribute("statusOptions", statusOptions);
        model.addAttribute("pageSize", cappedSize);
        return "message-tracking";
    }
}

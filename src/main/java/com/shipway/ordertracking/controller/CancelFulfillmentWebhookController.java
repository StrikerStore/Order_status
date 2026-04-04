package com.shipway.ordertracking.controller;

import com.shipway.ordertracking.dto.WebhookWrapper;
import com.shipway.ordertracking.service.CancelFulfillmentWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * External cancel webhook: {@code POST /cancel} (same JSON shape as other Claimio-style wrappers).
 */
@RestController
public class CancelFulfillmentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(CancelFulfillmentWebhookController.class);

    @Autowired
    private CancelFulfillmentWebhookService cancelFulfillmentWebhookService;

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> handleCancelFulfillment(@RequestBody WebhookWrapper wrapper) {
        log.info("Received cancel_fulfillment webhook (event={})", wrapper != null ? wrapper.getEvent() : null);

        if (wrapper == null || wrapper.getOrders() == null || wrapper.getOrders().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Empty webhook payload"));
        }

        Map<String, Object> result = cancelFulfillmentWebhookService.processCancelWebhook(wrapper);
        return ResponseEntity.ok(result);
    }
}

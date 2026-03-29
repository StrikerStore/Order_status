package com.shipway.ordertracking.controller;

import com.shipway.ordertracking.dto.BulkFulfillFromTrackingRequest;
import com.shipway.ordertracking.dto.BulkFulfillFromTrackingResponse;
import com.shipway.ordertracking.dto.UnfulfilledShopifyPreviewResponse;
import com.shipway.ordertracking.service.OrderTrackingBulkFulfillmentService;
import com.shipway.ordertracking.service.UnfulfilledShopifyPreviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preview GET plus POST to fulfill in Shopify when tracking is delivered / in transit / out for delivery only.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderFulfillmentPreviewController {

    private static final int MAX_FETCH_LIMIT = 2000;

    @Autowired
    private UnfulfilledShopifyPreviewService unfulfilledShopifyPreviewService;

    @Autowired
    private OrderTrackingBulkFulfillmentService orderTrackingBulkFulfillmentService;

    /**
     * Loads unfulfilled orders from Shopify (bulk GraphQL), then for each order loads the latest
     * {@code order_tracking} row and returns those whose {@code shipment_status} is in transit,
     * out for delivery, RTO, delivered, or undelivered (incl. picked up, not delivered).
     *
     * @param accountCode optional STRI / DRIB / … (omit to run all configured Shopify accounts)
     * @param limit       max Shopify unfulfilled orders loaded per account (capped by {@code shopify.preview.bulk-orders-max}), default 200, max 2000
     */
    @GetMapping("/unfulfilled-with-tracking-status")
    public ResponseEntity<UnfulfilledShopifyPreviewResponse> listUnfulfilledWithTrackingStatus(
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        int fetchLimit = Math.min(Math.max(limit, 1), MAX_FETCH_LIMIT);
        UnfulfilledShopifyPreviewResponse body = unfulfilledShopifyPreviewService.buildPreview(accountCode,
                fetchLimit);
        return ResponseEntity.ok(body);
    }

    /**
     * Runs the same discovery as the GET preview, then for each order whose {@code shipment_status} is
     * <strong>delivered</strong>, <strong>in transit</strong> (incl. picked up, INT, SHIPPED), or
     * <strong>out for delivery</strong>, creates a Shopify fulfillment (if still unfulfilled) or updates
     * fulfillment tracking / events. Other preview statuses (RTO, undelivered, booked, etc.) are skipped.
     *
     * @param body optional {@code accountCode}, {@code limit} (1–2000, default 500), {@code dryRun} (default false)
     */
    @PostMapping("/fulfill-from-tracking")
    public ResponseEntity<BulkFulfillFromTrackingResponse> fulfillFromTracking(
            @RequestBody(required = false) BulkFulfillFromTrackingRequest body) {
        if (body == null) {
            body = new BulkFulfillFromTrackingRequest();
        }
        return ResponseEntity.ok(orderTrackingBulkFulfillmentService.execute(body));
    }
}

package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.BulkFulfillFromTrackingRequest;
import com.shipway.ordertracking.dto.BulkFulfillFromTrackingResponse;
import com.shipway.ordertracking.dto.FulfillAttemptResult;
import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.dto.UnfulfilledShopifyOrderItem;
import com.shipway.ordertracking.dto.UnfulfilledShopifyPreviewResponse;
import com.shipway.ordertracking.repository.LabelAwbRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates Shopify fulfillments (or updates tracking) for unfulfilled orders whose
 * {@code order_tracking.shipment_status} maps to <strong>delivered</strong>, <strong>in transit</strong>,
 * or <strong>out for delivery</strong> only — a subset of the preview allowlist.
 */
@Service
public class OrderTrackingBulkFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderTrackingBulkFulfillmentService.class);

    private static final int MAX_LIMIT = 2000;

    @Autowired
    private UnfulfilledShopifyPreviewService unfulfilledShopifyPreviewService;

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private LabelAwbRepository labelAwbRepository;

    @Autowired
    private ShopifyProperties shopifyProperties;

    public BulkFulfillFromTrackingResponse execute(BulkFulfillFromTrackingRequest request) {
        boolean dryRun = request != null && request.isDryRun();

        int limit = 500;
        if (request != null && request.getLimit() != null) {
            limit = Math.min(Math.max(request.getLimit(), 1), MAX_LIMIT);
        }
        String accountCode = request != null ? request.getAccountCode() : null;

        UnfulfilledShopifyPreviewResponse preview = unfulfilledShopifyPreviewService.buildPreview(accountCode, limit);
        BulkFulfillFromTrackingResponse out = fulfillPreviewItemsInternal(preview.getItems(), dryRun);
        log.info("Bulk fulfill from tracking (API): dryRun={}, summary={}", dryRun, out.getSummary());
        return out;
    }

    /**
     * Fulfill / update Shopify for each preview row (e.g. fulfillment UI “apply all”). Same rules as {@link #execute}.
     */
    public BulkFulfillFromTrackingResponse fulfillPreviewItems(List<UnfulfilledShopifyOrderItem> items) {
        BulkFulfillFromTrackingResponse out = fulfillPreviewItemsInternal(items, false);
        log.info("Bulk fulfill preview items: summary={}", out.getSummary());
        return out;
    }

    private BulkFulfillFromTrackingResponse fulfillPreviewItemsInternal(List<UnfulfilledShopifyOrderItem> items,
            boolean dryRun) {
        BulkFulfillFromTrackingResponse out = new BulkFulfillFromTrackingResponse();
        out.setDryRun(dryRun);
        Map<String, Integer> summary = new HashMap<>();
        summary.put("previewItemsTotal", 0);
        summary.put("skippedNotDeliveredInTransitOrOfd", 0);
        summary.put("attempted", 0);
        summary.put("succeeded", 0);
        summary.put("failed", 0);

        List<UnfulfilledShopifyOrderItem> list = items != null ? items : List.of();
        summary.put("previewItemsTotal", list.size());

        for (UnfulfilledShopifyOrderItem item : list) {
            FulfillAttemptResult row = new FulfillAttemptResult();
            row.setAccountCode(item.getAccountCode());
            row.setOrderId(item.getOrderId());
            row.setOrderTrackingStatus(item.getOrderTrackingStatus());

            String shopifyStatus = mapTrackingStatusToShopifyShipmentStatus(item.getOrderTrackingStatus());
            if (shopifyStatus == null) {
                row.setSuccess(false);
                row.setMessage("Skipped: shipment_status is not delivered, in transit, or out for delivery");
                out.getResults().add(row);
                summary.merge("skippedNotDeliveredInTransitOrOfd", 1, Integer::sum);
                continue;
            }
            row.setShopifyShipmentStatus(shopifyStatus);

            if (dryRun) {
                row.setSuccess(true);
                row.setMessage("dryRun: would fulfill / update tracking");
                out.getResults().add(row);
                summary.merge("attempted", 1, Integer::sum);
                summary.merge("succeeded", 1, Integer::sum);
                continue;
            }

            summary.merge("attempted", 1, Integer::sum);
            String norm = ShopifyService.normalizeShopifyOrderNameKey(item.getOrderId());
            String awb = labelAwbRepository.findLatestAwb(item.getAccountCode(), norm).orElse(null);
            String trackingUrl = buildTrackingUrl(item.getAccountCode(), awb);
            boolean ok = fulfillOne(item.getAccountCode(), item.getOrderId(), shopifyStatus, awb, trackingUrl);
            row.setSuccess(ok);
            row.setMessage(ok ? "OK" : "Shopify fulfillment failed — see application logs");
            out.getResults().add(row);
            if (ok) {
                summary.merge("succeeded", 1, Integer::sum);
            } else {
                summary.merge("failed", 1, Integer::sum);
            }
        }

        out.setSummary(summary);
        return out;
    }

    /**
     * Fulfill / update Shopify for one order (UI or API). Only statuses that map to delivered, in transit, or OFD.
     */
    public FulfillAttemptResult fulfillSingleOrder(String accountCode, String orderId, String orderTrackingStatus) {
        FulfillAttemptResult r = new FulfillAttemptResult();
        r.setAccountCode(accountCode != null ? accountCode.toUpperCase() : null);
        r.setOrderId(orderId);
        r.setOrderTrackingStatus(orderTrackingStatus);

        log.info("fulfillSingleOrder start: account={} orderId={} orderTrackingStatus={}", accountCode, orderId,
                orderTrackingStatus);

        if (accountCode == null || accountCode.isBlank() || orderId == null || orderId.isBlank()) {
            r.setSuccess(false);
            r.setMessage("accountCode and orderId are required");
            log.warn("fulfillSingleOrder aborted: missing accountCode or orderId");
            return r;
        }

        String shopifyStatus = mapTrackingStatusToShopifyShipmentStatus(orderTrackingStatus);
        if (shopifyStatus == null) {
            r.setSuccess(false);
            r.setMessage("This shipment_status does not map to delivered, in transit, or out for delivery");
            log.warn("fulfillSingleOrder skipped: status not eligible for Shopify fulfill (delivered / in transit / OFD only): raw={}",
                    orderTrackingStatus);
            return r;
        }
        r.setShopifyShipmentStatus(shopifyStatus);

        String norm = ShopifyService.normalizeShopifyOrderNameKey(orderId);
        String awb = labelAwbRepository.findLatestAwb(accountCode, norm).orElse(null);
        String trackingUrl = buildTrackingUrl(accountCode, awb);
        log.info("fulfillSingleOrder: mapped shopifyShipmentStatus={} normalizedOrderKey={} awbFromLabels={} trackingUrlConfigured={}",
                shopifyStatus, norm, awb != null && !awb.isBlank(), trackingUrl != null && !trackingUrl.isBlank());

        boolean ok = fulfillOne(accountCode.trim(), orderId.trim(), shopifyStatus, awb, trackingUrl);
        r.setSuccess(ok);
        r.setMessage(ok ? "Shopify updated successfully" : "Failed — check server logs");
        log.info("fulfillSingleOrder end: account={} orderId={} success={}", accountCode.trim(), orderId.trim(), ok);
        return r;
    }

    /**
     * Maps {@code shipment_status} to Shopify tracking update / event input:
     * {@code delivered}, {@code in_transit}, {@code out_for_delivery}, or {@code null} if out of scope.
     */
    static String mapTrackingStatusToShopifyShipmentStatus(String shipmentStatus) {
        if (shipmentStatus == null || shipmentStatus.isBlank()) {
            return null;
        }
        String n = UnfulfilledShopifyPreviewService.normalizeShipmentStatus(shipmentStatus);

        if (n.contains("OUT FOR DELIVERY")) {
            return "out_for_delivery";
        }
        if (n.contains("NOT DELIVERED") || n.contains("UNDELIVERED")) {
            return null;
        }
        if (n.contains("DELIVERED")) {
            return "delivered";
        }
        if (n.contains("IN TRANSIT") || n.contains("PICKED UP") || n.equals("INT") || n.equals("SHIPPED")) {
            return "in_transit";
        }
        return null;
    }

    private String buildTrackingUrl(String accountCode, String awb) {
        if (awb == null || awb.isBlank()) {
            return null;
        }
        ShopifyAccount acc = shopifyProperties.getAccountByCode(accountCode);
        if (acc != null && acc.getTrackingUrlTemplate() != null && !acc.getTrackingUrlTemplate().isBlank()) {
            return acc.getTrackingUrlTemplate().replace("{awb}", awb.trim());
        }
        return null;
    }

    private boolean fulfillOne(String accountCode, String orderId, String shopifyShipmentStatus, String awb,
            String trackingUrl) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        if (orderId.contains("_")) {
            log.info("Skipping clone order_id: {}", orderId);
            return false;
        }

        Map<String, Object> orderNode = shopifyService.getOrderWithDisplayFulfillmentStatus(accountCode, orderId);
        if (orderNode == null) {
            log.warn("Order not found in Shopify: {} ({})", orderId, accountCode);
            return false;
        }

        String display = orderNode.get("displayFulfillmentStatus") != null
                ? orderNode.get("displayFulfillmentStatus").toString()
                : null;

        if (display != null && "FULFILLED".equalsIgnoreCase(display.trim())) {
            log.info("fulfillOne path: order already FULFILLED — update tracking only orderId={} ({})", orderId,
                    accountCode);
            return updateTrackingOnExistingFulfillment(accountCode, orderId, orderNode, shopifyShipmentStatus, awb);
        }

        log.info("fulfillOne path: create fulfillment + tracking orderId={} ({}) displayStatus={}", orderId, accountCode,
                display);

        String orderGid = orderNode.get("id") != null ? orderNode.get("id").toString() : null;
        if (orderGid == null) {
            log.warn("fulfillOne: missing order GID for {} ({})", orderId, accountCode);
            return false;
        }

        Map<String, Object> fulfillmentOrdersData = shopifyService.getFulfillmentOrdersForOrder(accountCode, orderGid);
        if (fulfillmentOrdersData == null) {
            log.warn("fulfillOne: no fulfillment orders payload for {} ({})", orderId, accountCode);
            return false;
        }

        String openFulfillmentOrderId = shopifyService.getOpenFulfillmentOrderIdFromEdges(fulfillmentOrdersData);
        if (openFulfillmentOrderId == null || openFulfillmentOrderId.isEmpty()) {
            log.warn("No OPEN fulfillment order for {} ({})", orderId, accountCode);
            return false;
        }

        Long numericOrderId = ShopifyService.parseNumericIdFromGid(orderGid, "gid://shopify/Order/");
        if (numericOrderId == null) {
            return false;
        }

        Long fulfillmentId = shopifyService.createFulfillment(accountCode, numericOrderId, openFulfillmentOrderId,
                awb, trackingUrl);
        if (fulfillmentId == null) {
            fulfillmentId = shopifyService.getFulfillmentId(accountCode, numericOrderId);
        }
        if (fulfillmentId == null) {
            log.warn("Could not create or resolve fulfillment for {} ({})", orderId, accountCode);
            return false;
        }

        if (!shopifyService.updateFulfillmentTracking(accountCode, numericOrderId, fulfillmentId, awb,
                shopifyShipmentStatus)) {
            log.error("updateFulfillmentTracking failed for {} ({})", orderId, accountCode);
            return false;
        }

        log.info("✅ Fulfilled / updated tracking for order {} ({}) status={}", orderId, accountCode,
                shopifyShipmentStatus);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean updateTrackingOnExistingFulfillment(String accountCode, String orderId,
            Map<String, Object> orderNode, String shopifyShipmentStatus, String awb) {
        Long numericOrderId = ShopifyService.parseNumericIdFromGid(
                orderNode.get("id") != null ? orderNode.get("id").toString() : null, "gid://shopify/Order/");
        if (numericOrderId == null) {
            return false;
        }

        Long fulfillmentId = null;
        Object fulfillmentsObj = orderNode.get("fulfillments");
        if (fulfillmentsObj instanceof List && !((List<?>) fulfillmentsObj).isEmpty()) {
            Object first = ((List<?>) fulfillmentsObj).get(0);
            if (first instanceof Map) {
                Object idObj = ((Map<?, ?>) first).get("id");
                if (idObj != null) {
                    fulfillmentId = ShopifyService.parseNumericIdFromGid(idObj.toString(),
                            "gid://shopify/Fulfillment/");
                }
            }
        }
        if (fulfillmentId == null) {
            fulfillmentId = shopifyService.getFulfillmentId(accountCode, numericOrderId);
        }
        if (fulfillmentId == null) {
            log.warn("No fulfillment id for already-fulfilled order {} ({})", orderId, accountCode);
            return false;
        }

        boolean ok = shopifyService.updateFulfillmentTracking(accountCode, numericOrderId, fulfillmentId, awb,
                shopifyShipmentStatus);
        if (ok) {
            log.info("✅ Updated tracking on existing fulfillment for {} ({}) status={} awb={}", orderId, accountCode,
                    shopifyShipmentStatus, awb != null ? "(set)" : "(none)");
        } else {
            log.error("updateFulfillmentTracking failed for already-fulfilled order {} ({}) fulfillmentId={}", orderId,
                    accountCode, fulfillmentId);
        }
        return ok;
    }
}

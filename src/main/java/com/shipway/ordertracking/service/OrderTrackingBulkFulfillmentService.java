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

            String shopifyKey = effectiveShopifyKey(item);
            String labelsAcct = item.getAccountCode();

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
            String awb = labelAwbRepository.findLatestAwb(labelsAcct, norm).orElse(null);
            String trackingUrl = buildTrackingUrl(shopifyKey, awb);
            boolean ok = fulfillOne(shopifyKey, labelsAcct, item.getOrderId(), shopifyStatus, awb, trackingUrl);
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

    /** Same as {@link #fulfillSingleOrder(String, String, String, String)} with no separate Shopify key (legacy). */
    public FulfillAttemptResult fulfillSingleOrder(String trackingAccountCode, String orderId,
            String orderTrackingStatus) {
        return fulfillSingleOrder(trackingAccountCode, orderId, orderTrackingStatus, null);
    }

    /**
     * Fulfill / update Shopify for one order (UI or API). Only statuses that map to delivered, in transit, or OFD.
     *
     * @param trackingAccountCode {@code order_tracking} / {@code labels} account_code
     * @param shopifyBrandName    optional {@code shopify.accounts} key; when blank, {@code trackingAccountCode} is used for Shopify too (legacy)
     */
    public FulfillAttemptResult fulfillSingleOrder(String trackingAccountCode, String orderId, String orderTrackingStatus,
            String shopifyBrandName) {
        FulfillAttemptResult r = new FulfillAttemptResult();
        r.setAccountCode(trackingAccountCode != null ? trackingAccountCode.trim().toUpperCase() : null);
        r.setOrderId(orderId);
        r.setOrderTrackingStatus(orderTrackingStatus);

        String shopifyKey = (shopifyBrandName != null && !shopifyBrandName.isBlank())
                ? shopifyBrandName.trim()
                : (trackingAccountCode != null ? trackingAccountCode.trim() : null);

        log.info("fulfillSingleOrder start: trackingAccount={} shopifyKey={} orderId={} orderTrackingStatus={}",
                trackingAccountCode, shopifyKey, orderId, orderTrackingStatus);

        if (trackingAccountCode == null || trackingAccountCode.isBlank() || orderId == null || orderId.isBlank()) {
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
        String awb = labelAwbRepository.findLatestAwb(trackingAccountCode.trim(), norm).orElse(null);
        String trackingUrl = buildTrackingUrl(shopifyKey, awb);
        log.info("fulfillSingleOrder: mapped shopifyShipmentStatus={} normalizedOrderKey={} awbFromLabels={} trackingUrlConfigured={}",
                shopifyStatus, norm, awb != null && !awb.isBlank(), trackingUrl != null && !trackingUrl.isBlank());

        boolean ok = fulfillOne(shopifyKey, trackingAccountCode.trim(), orderId.trim(), shopifyStatus, awb, trackingUrl);
        r.setSuccess(ok);
        r.setMessage(ok ? "Shopify updated successfully" : "Failed — check server logs");
        log.info("fulfillSingleOrder end: trackingAccount={} orderId={} success={}", trackingAccountCode.trim(),
                orderId.trim(), ok);
        return r;
    }

    private static String effectiveShopifyKey(UnfulfilledShopifyOrderItem item) {
        if (item.getShopifyBrandName() != null && !item.getShopifyBrandName().isBlank()) {
            return item.getShopifyBrandName().trim();
        }
        return item.getAccountCode() != null ? item.getAccountCode().trim() : "";
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

    /**
     * @param shopifyKey            {@code shopify.accounts} map key
     * @param trackingAccountCode   {@code labels} / {@code order_tracking} account_code (not sent to Shopify Admin)
     */
    private boolean fulfillOne(String shopifyKey, String trackingAccountCode, String orderId,
            String shopifyShipmentStatus, String awb, String trackingUrl) {
        log.debug("fulfillOne shopifyKey={} labelsAccount={} orderId={}", shopifyKey, trackingAccountCode, orderId);
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        if (orderId.contains("_")) {
            log.info("Skipping clone order_id: {}", orderId);
            return false;
        }

        Map<String, Object> orderNode = shopifyService.getOrderWithDisplayFulfillmentStatus(shopifyKey, orderId);
        if (orderNode == null) {
            log.warn("Order not found in Shopify: {} (shopifyKey={})", orderId, shopifyKey);
            return false;
        }

        String display = orderNode.get("displayFulfillmentStatus") != null
                ? orderNode.get("displayFulfillmentStatus").toString()
                : null;

        if (display != null && "FULFILLED".equalsIgnoreCase(display.trim())) {
            log.info("fulfillOne path: order already FULFILLED — update tracking only orderId={} (shopifyKey={})",
                    orderId, shopifyKey);
            return updateTrackingOnExistingFulfillment(shopifyKey, orderId, orderNode, shopifyShipmentStatus, awb);
        }

        log.info("fulfillOne path: create fulfillment + tracking orderId={} (shopifyKey={}) displayStatus={}", orderId,
                shopifyKey, display);

        String orderGid = orderNode.get("id") != null ? orderNode.get("id").toString() : null;
        if (orderGid == null) {
            log.warn("fulfillOne: missing order GID for {} (shopifyKey={})", orderId, shopifyKey);
            return false;
        }

        Map<String, Object> fulfillmentOrdersData = shopifyService.getFulfillmentOrdersForOrder(shopifyKey, orderGid);
        if (fulfillmentOrdersData == null) {
            log.warn("fulfillOne: no fulfillment orders payload for {} (shopifyKey={})", orderId, shopifyKey);
            return false;
        }

        String openFulfillmentOrderId = shopifyService.getOpenFulfillmentOrderIdFromEdges(fulfillmentOrdersData);
        if (openFulfillmentOrderId == null || openFulfillmentOrderId.isEmpty()) {
            log.warn("No OPEN fulfillment order for {} (shopifyKey={})", orderId, shopifyKey);
            return false;
        }

        Long numericOrderId = ShopifyService.parseNumericIdFromGid(orderGid, "gid://shopify/Order/");
        if (numericOrderId == null) {
            return false;
        }

        Long fulfillmentId = shopifyService.createFulfillment(shopifyKey, numericOrderId, openFulfillmentOrderId,
                awb, trackingUrl);
        if (fulfillmentId == null) {
            fulfillmentId = shopifyService.getFulfillmentId(shopifyKey, numericOrderId);
        }
        if (fulfillmentId == null) {
            log.warn("Could not create or resolve fulfillment for {} (shopifyKey={})", orderId, shopifyKey);
            return false;
        }

        if (!shopifyService.updateFulfillmentTracking(shopifyKey, numericOrderId, fulfillmentId, awb,
                shopifyShipmentStatus)) {
            log.error("updateFulfillmentTracking failed for {} (shopifyKey={})", orderId, shopifyKey);
            return false;
        }

        log.info("✅ Fulfilled / updated tracking for order {} (shopifyKey={}) status={}", orderId, shopifyKey,
                shopifyShipmentStatus);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean updateTrackingOnExistingFulfillment(String shopifyKey, String orderId,
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
            fulfillmentId = shopifyService.getFulfillmentId(shopifyKey, numericOrderId);
        }
        if (fulfillmentId == null) {
            log.warn("No fulfillment id for already-fulfilled order {} (shopifyKey={})", orderId, shopifyKey);
            return false;
        }

        boolean ok = shopifyService.updateFulfillmentTracking(shopifyKey, numericOrderId, fulfillmentId, awb,
                shopifyShipmentStatus);
        if (ok) {
            log.info("✅ Updated tracking on existing fulfillment for {} (shopifyKey={}) status={} awb={}", orderId,
                    shopifyKey, shopifyShipmentStatus, awb != null ? "(set)" : "(none)");
        } else {
            log.error("updateFulfillmentTracking failed for already-fulfilled order {} (shopifyKey={}) fulfillmentId={}",
                    orderId, shopifyKey, fulfillmentId);
        }
        return ok;
    }
}

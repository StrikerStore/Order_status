package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.dto.UnfulfilledShopifyOrderItem;
import com.shipway.ordertracking.dto.UnfulfilledShopifyPreviewResponse;
import com.shipway.ordertracking.entity.OrderTracking;
import com.shipway.ordertracking.entity.StoreShopifyConnection;
import com.shipway.ordertracking.repository.OrderTrackingRepository;
import com.shipway.ordertracking.repository.StoreShopifyConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Shopify-first preview: load unfulfilled orders from Shopify, then attach {@code order_tracking}
 * when {@code shipment_status} matches the configured carrier allowlist.
 */
@Service
public class UnfulfilledShopifyPreviewService {

    private static final Logger log = LoggerFactory.getLogger(UnfulfilledShopifyPreviewService.class);

    private static final int ORDER_ID_IN_CHUNK = 500;

    /**
     * Normalized ({@code trim}, {@code UPPER}, {@code _} → space, collapse spaces) carrier
     * {@code shipment_status} values that qualify for the preview API.
     */
    private static final Set<String> ALLOWED_SHIPMENT_STATUSES;

    static {
        String[] raw = new String[] {
                "0",
                "Address Incorrect",
                "AWB_ASSIGNED",
                "Consignee Refused",
                "Consignee Unavailable",
                "CROV",
                "Customer refused - OTP verified",
                "DEL",
                "DELAYED",
                "Delivered",
                "Delivery Delayed",
                "Delivery Reattempt",
                "Future delivery requested",
                "In Transit",
                "INT",
                "LOST",
                "Manifest Uploaded",
                "Out For Delivery",
                "Out for Pickup",
                "OUT_FOR_PICKUP",
                "Picked Up",
                "PICKED_UP",
                "Pickup Failed",
                "PICKUP_GENERATED",
                "Reached At Destination",
                "REACHED_AT_DESTINATION_HUB",
                "RTD",
                "RTO",
                "RTO Delivered",
                "RTO In Transit",
                "RTO Initiated",
                "RTO Lost",
                "RTO Undelivered",
                "RTO_IN_TRANSIT",
                "RTO_INITIATED",
                "RTO_NDR",
                "RTO_OFD",
                "RTONDR12",
                "RTONDR5",
                "RTOUND",
                "Shipment Booked",
                "SHIPPED",
                "SHNDR1",
                "SHNDR12",
                "SHNDR13",
                "SHNDR14",
                "SHNDR16",
                "SHNDR18",
                "SHNDR2",
                "SHNDR3",
                "SHNDR4",
                "SHNDR5",
                "SHNDR6",
                "SHNDR8",
                "SHPFR1",
                "SHPFR10",
                "SHPFR11",
                "SHPFR3",
                "SHPFR4",
                "SHPFR6",
                "SHPFR7",
                "Undelivered",
        };
        Set<String> set = new HashSet<>();
        for (String s : raw) {
            set.add(normalizeShipmentStatus(s));
        }
        ALLOWED_SHIPMENT_STATUSES = Collections.unmodifiableSet(set);
    }

    @Autowired
    private OrderTrackingRepository orderTrackingRepository;

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private ShopifyProperties shopifyProperties;

    @Autowired
    private StoreShopifyConnectionRepository storeShopifyConnectionRepository;

    @Value("${shopify.preview.bulk-orders-query}")
    private String bulkOrdersQuery;

    @Value("${shopify.preview.bulk-orders-max:5000}")
    private int bulkOrdersMaxDefault;

    /**
     * @param filter           optional {@code store_shopify_connections.brand_name} or {@code account_code};
     *                         when the mapping table is empty, treated as legacy {@code shopify.accounts} key
     * @param shopifyBulkCapPerAccount max unfulfilled orders to load from Shopify per account (request limit)
     */
    public UnfulfilledShopifyPreviewResponse buildPreview(String filter, int shopifyBulkCapPerAccount) {
        UnfulfilledShopifyPreviewResponse response = new UnfulfilledShopifyPreviewResponse();
        Map<String, Integer> counts = new HashMap<>();
        counts.put("shopifyUnfulfilledInBulk", 0);
        counts.put("skippedShopifyCloneOrderKey", 0);
        counts.put("skippedNoOrderTrackingRow", 0);
        counts.put("skippedTrackingStatusMismatch", 0);
        counts.put("matchedWithOrderTracking", 0);
        counts.put("accountsProcessed", 0);

        int cap = Math.min(Math.max(shopifyBulkCapPerAccount, 1), 10_000);
        int bulkMax = Math.min(cap, Math.max(1, bulkOrdersMaxDefault));

        List<ShopifyTrackingPair> pairs = resolveShopifyTrackingPairs(filter);
        if (pairs.isEmpty()) {
            log.warn("No Shopify / mapping rows to process (filter: {})", filter);
            response.setCounts(counts);
            return response;
        }

        for (ShopifyTrackingPair pair : pairs) {
            String shopifyKey = pair.shopifyBrandName();
            String trackingAcct = pair.trackingAccountCode();
            if (shopifyProperties.getAccountByCode(shopifyKey) == null) {
                log.debug("Skipping unknown Shopify account key (brand_name): {}", shopifyKey);
                continue;
            }
            increment(counts, "accountsProcessed");

            Map<String, Map<String, Object>> shopifyByKey = shopifyService.loadOrderNodesBySearchQueryPaged(shopifyKey,
                    bulkOrdersQuery, bulkMax);
            counts.put("shopifyUnfulfilledInBulk",
                    counts.getOrDefault("shopifyUnfulfilledInBulk", 0) + shopifyByKey.size());

            logGraphqlUnfulfilledIds(shopifyKey, shopifyByKey);

            List<String> keysForDb = new ArrayList<>();
            for (String normKey : shopifyByKey.keySet()) {
                if (normKey.contains("_")) {
                    increment(counts, "skippedShopifyCloneOrderKey");
                    continue;
                }
                keysForDb.add(normKey);
            }

            Map<String, OrderTracking> trackingByKey = loadLatestTrackingForKeys(trackingAcct, keysForDb);

            logOrderTrackingHitsRaw(trackingAcct, trackingByKey);

            int acctMatched = 0;
            int acctNoRow = 0;
            int acctMismatch = 0;

            for (Map.Entry<String, Map<String, Object>> e : shopifyByKey.entrySet()) {
                String orderKey = e.getKey();
                if (orderKey.contains("_")) {
                    continue;
                }

                Map<String, Object> orderNode = e.getValue();
                OrderTracking tr = trackingByKey.get(orderKey);
                if (tr == null) {
                    acctNoRow++;
                    increment(counts, "skippedNoOrderTrackingRow");
                    continue;
                }

                if (!matchesUserRequestedStatuses(tr.getShipmentStatus())) {
                    acctMismatch++;
                    increment(counts, "skippedTrackingStatusMismatch");
                    continue;
                }

                String display = orderNode.get("displayFulfillmentStatus") != null
                        ? orderNode.get("displayFulfillmentStatus").toString()
                        : null;

                UnfulfilledShopifyOrderItem item = new UnfulfilledShopifyOrderItem();
                item.setAccountCode(trackingAcct.toUpperCase());
                item.setShopifyBrandName(shopifyKey.toUpperCase());
                item.setOrderId(tr.getOrderId());
                item.setOrderTrackingStatus(tr.getShipmentStatus());
                item.setShopifyDisplayFulfillmentStatus(display);

                Object gidObj = orderNode.get("id");
                if (gidObj != null) {
                    String gid = gidObj.toString();
                    item.setShopifyOrderGid(gid);
                    item.setShopifyOrderNumericId(extractNumericShopifyOrderId(gid));
                }

                Object nameObj = orderNode.get("name");
                if (nameObj != null) {
                    item.setShopifyOrderName(nameObj.toString());
                }

                Object createdAtObj = orderNode.get("createdAt");
                if (createdAtObj != null) {
                    item.setShopifyCreatedAt(createdAtObj.toString());
                }

                response.getItems().add(item);
                acctMatched++;
                increment(counts, "matchedWithOrderTracking");
            }

            log.info(
                    "Preview shopifyKey={} trackingAccount={}: shipment_status allowlist ({} values) applied — matchedItems={}, skippedNoTrackingRow={}, skippedStatusMismatch={}",
                    shopifyKey.toUpperCase(), trackingAcct.toUpperCase(), ALLOWED_SHIPMENT_STATUSES.size(), acctMatched,
                    acctNoRow, acctMismatch);
        }

        List<String> orderIds = new ArrayList<>();
        for (UnfulfilledShopifyOrderItem item : response.getItems()) {
            if (item.getOrderId() != null && !item.getOrderId().isBlank()) {
                orderIds.add(item.getOrderId());
            }
        }
        response.setOrderIds(orderIds);
        log.info("Unfulfilled + tracking preview: {} matching order id(s): {}", orderIds.size(), orderIds);

        response.setCounts(counts);
        return response;
    }

    /** INFO: GraphQL {@code name} and numeric order id from each unfulfilled node. */
    private void logGraphqlUnfulfilledIds(String accountCode, Map<String, Map<String, Object>> shopifyByKey) {
        TreeSet<String> names = new TreeSet<>();
        TreeSet<String> numericIds = new TreeSet<>();
        for (Map<String, Object> node : shopifyByKey.values()) {
            Object nameObj = node.get("name");
            if (nameObj != null) {
                names.add(nameObj.toString());
            }
            Object idObj = node.get("id");
            if (idObj != null) {
                String num = extractNumericShopifyOrderId(idObj.toString());
                if (num != null && !num.isBlank()) {
                    numericIds.add(num);
                }
            }
        }
        log.info(
                "Preview account={}: GraphQL unfulfilled count={}, order name(s)={}, Shopify admin numeric order id(s)={}",
                accountCode.toUpperCase(), shopifyByKey.size(), names, numericIds);
    }

    /**
     * INFO: latest {@code order_tracking} row per key for unfulfilled Shopify orders — logged
     * <strong>before</strong> {@link #matchesUserRequestedStatuses(String)} (carrier allowlist).
     */
    private void logOrderTrackingHitsRaw(String accountCode, Map<String, OrderTracking> trackingByKey) {
        Map<String, String> keyToStatus = new TreeMap<>();
        for (Map.Entry<String, OrderTracking> e : trackingByKey.entrySet()) {
            OrderTracking tr = e.getValue();
            String st = tr.getShipmentStatus() != null ? tr.getShipmentStatus() : "";
            keyToStatus.put(e.getKey(), st);
        }
        log.info(
                "Preview account={}: order_tracking DB hit(s) count={} (before status filter) normalizedKey -> shipment_status={}",
                accountCode.toUpperCase(), trackingByKey.size(), keyToStatus);
    }

    private record ShopifyTrackingPair(String shopifyBrandName, String trackingAccountCode) {
    }

    /**
     * When {@code store_shopify_connections} has rows: Shopify uses {@code brand_name}, {@code order_tracking} /
     * {@code labels} use {@code account_code}. When the table is empty, both sides use the same legacy
     * {@code shopify.accounts} key.
     */
    private List<ShopifyTrackingPair> resolveShopifyTrackingPairs(String filter) {
        if (storeShopifyConnectionRepository.count() == 0) {
            List<ShopifyTrackingPair> out = new ArrayList<>();
            for (String k : resolveLegacyShopifyKeys(filter)) {
                out.add(new ShopifyTrackingPair(k, k));
            }
            return out;
        }

        List<StoreShopifyConnection> all = storeShopifyConnectionRepository.findAllByOrderByBrandNameAsc();
        if (filter == null || filter.isBlank()) {
            return all.stream()
                    .map(c -> new ShopifyTrackingPair(c.getBrandName().trim(), c.getAccountCode().trim()))
                    .toList();
        }
        String f = filter.trim();
        for (StoreShopifyConnection c : all) {
            if (c.getBrandName() != null && c.getBrandName().trim().equalsIgnoreCase(f)) {
                return List.of(new ShopifyTrackingPair(c.getBrandName().trim(), c.getAccountCode().trim()));
            }
        }
        for (StoreShopifyConnection c : all) {
            if (c.getAccountCode() != null && c.getAccountCode().trim().equalsIgnoreCase(f)) {
                return List.of(new ShopifyTrackingPair(c.getBrandName().trim(), c.getAccountCode().trim()));
            }
        }
        log.warn("store_shopify_connections has {} row(s) but none matched filter '{}'", all.size(), filter);
        return List.of();
    }

    private List<String> resolveLegacyShopifyKeys(String accountCode) {
        List<String> out = new ArrayList<>();
        if (accountCode != null && !accountCode.isBlank()) {
            out.add(accountCode.trim().toUpperCase());
            return out;
        }
        out.addAll(new TreeSet<>(shopifyProperties.getAccounts().keySet()));
        return out;
    }

    private Map<String, OrderTracking> loadLatestTrackingForKeys(String accountCode, List<String> normKeys) {
        Map<String, OrderTracking> out = new HashMap<>();
        if (normKeys == null || normKeys.isEmpty()) {
            return out;
        }
        String acct = accountCode.trim();
        for (int i = 0; i < normKeys.size(); i += ORDER_ID_IN_CHUNK) {
            int end = Math.min(i + ORDER_ID_IN_CHUNK, normKeys.size());
            List<String> chunk = normKeys.subList(i, end);
            List<OrderTracking> rows = orderTrackingRepository.findLatestByAccountAndNormalizedOrderIds(acct, chunk);
            for (OrderTracking ot : rows) {
                String nk = ShopifyService.normalizeShopifyOrderNameKey(ot.getOrderId());
                if (!nk.isEmpty()) {
                    out.put(nk, ot);
                }
            }
        }
        return out;
    }

    /**
     * Whether {@code shipment_status} is in the carrier allowlist ({@link #ALLOWED_SHIPMENT_STATUSES}),
     * after the same normalization as the list entries.
     */
    static boolean matchesUserRequestedStatuses(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return ALLOWED_SHIPMENT_STATUSES.contains(normalizeShipmentStatus(status));
    }

    static String normalizeShipmentStatus(String status) {
        if (status == null) {
            return "";
        }
        String n = status.trim().toUpperCase().replace('_', ' ');
        return n.replaceAll("\\s+", " ").trim();
    }

    private static String extractNumericShopifyOrderId(String gid) {
        if (gid == null) {
            return null;
        }
        String prefix = "gid://shopify/Order/";
        if (gid.startsWith(prefix)) {
            return gid.substring(prefix.length());
        }
        return null;
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }
}

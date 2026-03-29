package com.shipway.ordertracking.controller;

import com.shipway.ordertracking.dto.BulkFulfillFromTrackingResponse;
import com.shipway.ordertracking.dto.FulfillAttemptResult;
import com.shipway.ordertracking.dto.UnfulfilledShopifyOrderItem;
import com.shipway.ordertracking.dto.UnfulfilledShopifyPreviewResponse;
import com.shipway.ordertracking.repository.LabelAwbRepository;
import com.shipway.ordertracking.service.OrderTrackingBulkFulfillmentService;
import com.shipway.ordertracking.service.ShopifyService;
import com.shipway.ordertracking.service.UnfulfilledShopifyPreviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thymeleaf UI: load unfulfilled + tracking preview table, apply Shopify fulfillment per row.
 */
@Controller
@RequestMapping("/ui/fulfillment")
public class FulfillmentUiController {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentUiController.class);

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2000;

    @Autowired
    private UnfulfilledShopifyPreviewService unfulfilledShopifyPreviewService;

    @Autowired
    private OrderTrackingBulkFulfillmentService orderTrackingBulkFulfillmentService;

    @Autowired
    private LabelAwbRepository labelAwbRepository;

    @Value("${fulfillment.ui.shopify-created-on-or-after:2026-01-27}")
    private String shopifyCreatedOnOrAfter;

    @GetMapping
    public String index(Model model) {
        if (!model.containsAttribute("items")) {
            model.addAttribute("items", List.of());
        }
        if (!model.containsAttribute("lastLimit")) {
            model.addAttribute("lastLimit", DEFAULT_LIMIT);
        }
        if (!model.containsAttribute("lastAccountCode")) {
            model.addAttribute("lastAccountCode", "");
        }
        addShopifyCreatedCutoffToModel(model);
        return "fulfillment-dashboard";
    }

    @PostMapping("/load")
    public String load(
            @RequestParam(required = false) String accountCode,
            @RequestParam(required = false, defaultValue = "200") int limit,
            Model model) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        String acct = accountCode != null && !accountCode.isBlank() ? accountCode.trim() : null;

        UnfulfilledShopifyPreviewResponse preview = unfulfilledShopifyPreviewService.buildPreview(acct, capped);

        List<UnfulfilledShopifyOrderItem> filtered = filterItemsByShopifyCreatedOnOrAfterUiCutoff(preview.getItems());
        List<UnfulfilledShopifyOrderItem> sorted = sortPreviewItemsByOrderId(filtered);
        attachAwbsFromLabelTable(sorted);
        model.addAttribute("items", sorted);
        model.addAttribute("counts", preview.getCounts());
        model.addAttribute("orderIds", sorted.stream().map(UnfulfilledShopifyOrderItem::getOrderId).filter(Objects::nonNull)
                .toList());
        model.addAttribute("lastAccountCode", accountCode != null ? accountCode.trim() : "");
        model.addAttribute("lastLimit", capped);
        model.addAttribute("flashMessage", "Loaded " + sorted.size() + " row(s) (Shopify created on or after "
                + formatUiShopifyCreatedCutoffForMessage() + "; " + preview.getItems().size()
                + " before UI date filter).");
        model.addAttribute("flashSuccess", true);
        addShopifyCreatedCutoffToModel(model);
        return "fulfillment-dashboard";
    }

    @PostMapping("/apply")
    public String apply(
            @RequestParam String accountCode,
            @RequestParam String orderId,
            @RequestParam String orderTrackingStatus,
            @RequestParam(required = false) String lastAccountCode,
            @RequestParam(required = false, defaultValue = "200") int lastLimit,
            Model model) {

        log.info("UI Apply to Shopify: accountCode={} orderId={} orderTrackingStatus={}", accountCode, orderId,
                orderTrackingStatus);
        FulfillAttemptResult result = orderTrackingBulkFulfillmentService.fulfillSingleOrder(accountCode, orderId,
                orderTrackingStatus);
        log.info("UI Apply to Shopify finished: orderId={} success={} message={}", orderId, result.isSuccess(),
                result.getMessage());
        model.addAttribute("applyResult", result);
        model.addAttribute("flashMessage", result.getMessage());
        model.addAttribute("flashSuccess", result.isSuccess());

        int capped = Math.min(Math.max(lastLimit, 1), MAX_LIMIT);
        String acct = lastAccountCode != null && !lastAccountCode.isBlank() ? lastAccountCode.trim() : null;
        refreshFulfillmentTableModel(model, acct, capped, lastAccountCode);
        addShopifyCreatedCutoffToModel(model);
        return "fulfillment-dashboard";
    }

    @PostMapping("/apply-all")
    public String applyAll(
            @RequestParam(required = false) String lastAccountCode,
            @RequestParam(required = false, defaultValue = "200") int lastLimit,
            Model model) {

        int capped = Math.min(Math.max(lastLimit, 1), MAX_LIMIT);
        String acct = lastAccountCode != null && !lastAccountCode.isBlank() ? lastAccountCode.trim() : null;

        UnfulfilledShopifyPreviewResponse preview = unfulfilledShopifyPreviewService.buildPreview(acct, capped);
        List<UnfulfilledShopifyOrderItem> filtered = filterItemsByShopifyCreatedOnOrAfterUiCutoff(preview.getItems());
        List<UnfulfilledShopifyOrderItem> sorted = sortPreviewItemsByOrderId(filtered);
        attachAwbsFromLabelTable(sorted);

        log.info("UI Apply all to Shopify: visibleRows={} accountFilter={} limit={}", sorted.size(), acct, capped);
        BulkFulfillFromTrackingResponse bulk = orderTrackingBulkFulfillmentService.fulfillPreviewItems(sorted);

        Map<String, Integer> sum = bulk.getSummary();
        String flash = String.format(Locale.ENGLISH,
                "Apply all finished: attempted=%d, succeeded=%d, failed=%d, skipped (tracking status not eligible)=%d — %d row(s) in table.",
                sum.getOrDefault("attempted", 0),
                sum.getOrDefault("succeeded", 0),
                sum.getOrDefault("failed", 0),
                sum.getOrDefault("skippedNotDeliveredInTransitOrOfd", 0),
                sum.getOrDefault("previewItemsTotal", 0));
        boolean flashSuccess = sum.getOrDefault("failed", 0) == 0;
        model.addAttribute("flashMessage", flash);
        model.addAttribute("flashSuccess", flashSuccess);

        refreshFulfillmentTableModel(model, acct, capped, lastAccountCode);
        addShopifyCreatedCutoffToModel(model);
        return "fulfillment-dashboard";
    }

    /** Reload preview + UI date filter + AWBs into the model (same rules as after single apply). */
    private void refreshFulfillmentTableModel(Model model, String acct, int capped, String lastAccountCodeForForm) {
        UnfulfilledShopifyPreviewResponse preview = unfulfilledShopifyPreviewService.buildPreview(acct, capped);
        List<UnfulfilledShopifyOrderItem> filtered = filterItemsByShopifyCreatedOnOrAfterUiCutoff(preview.getItems());
        List<UnfulfilledShopifyOrderItem> sorted = sortPreviewItemsByOrderId(filtered);
        attachAwbsFromLabelTable(sorted);
        model.addAttribute("items", sorted);
        model.addAttribute("counts", preview.getCounts());
        model.addAttribute("orderIds", sorted.stream().map(UnfulfilledShopifyOrderItem::getOrderId).filter(Objects::nonNull)
                .toList());
        model.addAttribute("lastAccountCode", lastAccountCodeForForm != null ? lastAccountCodeForForm.trim() : "");
        model.addAttribute("lastLimit", capped);
    }

    private void addShopifyCreatedCutoffToModel(Model model) {
        try {
            LocalDate d = LocalDate.parse(shopifyCreatedOnOrAfter.trim());
            model.addAttribute("shopifyCreatedCutoffNote",
                    d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)));
        } catch (DateTimeException e) {
            model.addAttribute("shopifyCreatedCutoffNote", shopifyCreatedOnOrAfter.trim());
        }
    }

    private String formatUiShopifyCreatedCutoffForMessage() {
        try {
            return LocalDate.parse(shopifyCreatedOnOrAfter.trim())
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
        } catch (DateTimeException e) {
            return shopifyCreatedOnOrAfter.trim();
        }
    }

    private Instant uiShopifyCreatedCutoffInstant() {
        LocalDate d = LocalDate.parse(shopifyCreatedOnOrAfter.trim());
        return d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * UI-only: drop rows with missing/unparseable {@code shopifyCreatedAt} or created strictly before the cutoff
     * (cutoff = start of {@link #shopifyCreatedOnOrAfter} in UTC).
     */
    private List<UnfulfilledShopifyOrderItem> filterItemsByShopifyCreatedOnOrAfterUiCutoff(
            List<UnfulfilledShopifyOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Instant cutoff = uiShopifyCreatedCutoffInstant();
        return items.stream()
                .filter(i -> {
                    Instant t = parseShopifyCreatedAt(i.getShopifyCreatedAt());
                    return t != null && !t.isBefore(cutoff);
                })
                .collect(Collectors.toList());
    }

    private static Instant parseShopifyCreatedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            /* fall through */
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignored) {
            /* fall through */
        }
        try {
            return ZonedDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private void attachAwbsFromLabelTable(List<UnfulfilledShopifyOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, List<UnfulfilledShopifyOrderItem>> byAccount = items.stream()
                .filter(i -> i.getAccountCode() != null && !i.getAccountCode().isBlank())
                .collect(Collectors.groupingBy(i -> i.getAccountCode().trim().toUpperCase()));
        for (Map.Entry<String, List<UnfulfilledShopifyOrderItem>> e : byAccount.entrySet()) {
            String acct = e.getKey();
            List<String> normKeys = e.getValue().stream()
                    .map(i -> ShopifyService.normalizeShopifyOrderNameKey(i.getOrderId()))
                    .filter(k -> !k.isEmpty())
                    .distinct()
                    .toList();
            Map<String, String> awbByKey = labelAwbRepository.findLatestAwbsForAccountAndNormalizedOrderIds(acct,
                    normKeys);
            for (UnfulfilledShopifyOrderItem i : e.getValue()) {
                String k = ShopifyService.normalizeShopifyOrderNameKey(i.getOrderId());
                i.setAwb(awbByKey.get(k));
            }
        }
    }

    /**
     * Sort by numeric order (Shopify id, else digits in {@code order_id}), then by {@code orderId} string.
     */
    private static List<UnfulfilledShopifyOrderItem> sortPreviewItemsByOrderId(List<UnfulfilledShopifyOrderItem> source) {
        List<UnfulfilledShopifyOrderItem> copy = new ArrayList<>(source);
        copy.sort(Comparator
                .comparingLong(FulfillmentUiController::sortKeyNumericOrder)
                .thenComparing(i -> i.getOrderId() != null ? i.getOrderId() : "", String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    private static long sortKeyNumericOrder(UnfulfilledShopifyOrderItem i) {
        if (i == null) {
            return Long.MAX_VALUE;
        }
        String num = i.getShopifyOrderNumericId();
        if (num != null && !num.isBlank()) {
            try {
                return Long.parseLong(num.trim());
            } catch (NumberFormatException ignored) {
                /* fall through */
            }
        }
        String oid = i.getOrderId();
        if (oid != null) {
            String digits = oid.replace("#", "").trim();
            try {
                return Long.parseLong(digits);
            } catch (NumberFormatException ignored) {
                /* fall through */
            }
        }
        return Long.MAX_VALUE;
    }
}

package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.dto.StatusUpdateWebhook;
import com.shipway.ordertracking.dto.WebhookWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles {@code cancel_fulfillment} webhooks: cancel Shopify fulfillment via GraphQL (no DB deletes).
 */
@Service
public class CancelFulfillmentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(CancelFulfillmentWebhookService.class);

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private ShopifyProperties shopifyProperties;

    /**
     * Shopify store key from {@code brand_name} only ({@link StatusUpdateWebhook.OrderStatus#resolveBrandName()}).
     */
    private String resolveShopifyAccountKey(StatusUpdateWebhook.OrderStatus order) {
        String brand = order.resolveBrandName();
        return brand != null ? brand.trim().toUpperCase() : null;
    }

    public Map<String, Object> processCancelWebhook(WebhookWrapper wrapper) {
        Map<String, Object> out = new HashMap<>();
        if (wrapper == null || wrapper.getOrders() == null || wrapper.getOrders().isEmpty()) {
            out.put("success", false);
            out.put("error", "Empty webhook payload");
            return out;
        }

        List<StatusUpdateWebhook.OrderStatus> orders = wrapper.getOrders();
        int ok = 0;
        int fail = 0;
        for (StatusUpdateWebhook.OrderStatus order : orders) {
            Map<String, Object> one = processOneOrder(order);
            if (Boolean.TRUE.equals(one.get("success"))) {
                ok++;
            } else {
                fail++;
            }
        }

        out.put("success", fail == 0);
        out.put("processed", orders.size());
        out.put("successCount", ok);
        out.put("failCount", fail);
        return out;
    }

    private Map<String, Object> processOneOrder(StatusUpdateWebhook.OrderStatus order) {
        Map<String, Object> result = new HashMap<>();
        String orderId = order.getOrderId();

        if (orderId == null || orderId.isBlank()) {
            result.put("success", false);
            result.put("message", "Missing order_id");
            return result;
        }

        if (orderId.contains("_")) {
            log.info("Order {} skipped for cancel (clone id contains '_')", orderId);
            result.put("success", false);
            result.put("message", "Skipped clone order");
            return result;
        }

        String shopifyKey = resolveShopifyAccountKey(order);
        if (shopifyKey == null || shopifyKey.isEmpty()) {
            result.put("success", false);
            result.put("message", "Missing brand_name");
            return result;
        }

        if (shopifyProperties.getAccountByCode(shopifyKey) == null) {
            log.warn("No shopify.accounts entry for key: {}", shopifyKey);
            result.put("success", false);
            result.put("message", "Unknown Shopify account: " + shopifyKey);
            return result;
        }

        boolean cancelled = shopifyService.cancelFulfillmentForOrder(shopifyKey, orderId, order.getAwb());
        if (!cancelled) {
            result.put("success", false);
            result.put("message", "Shopify fulfillment cancel failed");
            result.put("orderId", orderId);
            return result;
        }

        log.info("Fulfillment cancelled for order {} (shopify account {})", orderId, shopifyKey);
        result.put("success", true);
        result.put("message", "Fulfillment cancelled");
        result.put("orderId", orderId);
        return result;
    }
}

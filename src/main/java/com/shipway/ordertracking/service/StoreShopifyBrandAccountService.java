package com.shipway.ordertracking.service;

import com.shipway.ordertracking.entity.StoreShopifyConnection;
import com.shipway.ordertracking.repository.StoreShopifyConnectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves carrier / DB {@code account_code} for a Shopify config key ({@code shopify.accounts} / {@code brand_name}
 * in {@code store_shopify_connections}).
 * <p>
 * Matching is tolerant of spacing and case: e.g. app key {@code strikerstore} matches DB {@code STRIKER STORE},
 * {@code Striker Store}, or {@code strikerstore} (same normalization as {@code shopify.accounts} lookups).
 */
@Service
public class StoreShopifyBrandAccountService {

    @Autowired
    private StoreShopifyConnectionRepository storeShopifyConnectionRepository;

    /**
     * @param shopifyBrandKey e.g. {@code strikerstore} (from config / flows)
     * @return {@code account_code} for {@code order_tracking} / {@code labels}, if mapped
     */
    public Optional<String> findTrackingAccountCode(String shopifyBrandKey) {
        if (shopifyBrandKey == null || shopifyBrandKey.isBlank()) {
            return Optional.empty();
        }
        String wanted = normalizeBrandKey(shopifyBrandKey.trim());
        return storeShopifyConnectionRepository.findAllByOrderByBrandNameAsc().stream()
                .filter(c -> c.getBrandName() != null && !c.getBrandName().isBlank())
                .filter(c -> normalizeBrandKey(c.getBrandName()).equals(wanted))
                .map(StoreShopifyConnection::getAccountCode)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst();
    }

    /** Same normalization as {@code ShopifyProperties} / {@code BotspaceProperties} map lookup (spaces removed, lower case). */
    public static String normalizeBrandKey(String key) {
        return key.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}

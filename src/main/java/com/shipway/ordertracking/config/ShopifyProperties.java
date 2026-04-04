package com.shipway.ordertracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "shopify")
public class ShopifyProperties {

    // Map of account codes to Shopify account configurations
    private Map<String, ShopifyAccount> accounts = new HashMap<>();

    // Legacy single account support (for backward compatibility)

    public ShopifyProperties() {
    }

    public Map<String, ShopifyAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, ShopifyAccount> accounts) {
        this.accounts = accounts;
    }

    /**
     * Get Shopify account configuration by map key ({@code shopify.accounts.*}).
     * Matches exact key, then a space-insensitive lowercase form (e.g. {@code STRIKER STORE} → {@code strikerstore}).
     */
    public ShopifyAccount getAccountByCode(String accountCode) {
        if (accountCode == null || accountCode.isEmpty()) {
            return null;
        }
        String trimmed = accountCode.trim();
        ShopifyAccount direct = accounts.get(trimmed);
        if (direct != null) {
            return direct;
        }
        String norm = normalizeAccountMapKey(trimmed);
        for (Map.Entry<String, ShopifyAccount> e : accounts.entrySet()) {
            if (e.getKey() != null && normalizeAccountMapKey(e.getKey()).equals(norm)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String normalizeAccountMapKey(String key) {
        return key.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    /** Shopify Admin API version (e.g. 2025-07) */
    private static final String API_VERSION = "2025-07";

    /**
     * Build the full Shopify API URL for a given shop name
     */
    public static String buildApiUrl(String shop) {
        if (shop != null && !shop.isEmpty()) {
            // Remove https:// and .myshopify.com if present
            String shopName = shop.replace("https://", "").replace(".myshopify.com", "");
            return "https://" + shopName + ".myshopify.com/admin/api/" + API_VERSION;
        }
        return "";
    }

    /**
     * Build the Shopify GraphQL URL for a given shop name
     */
    public static String buildGraphQLUrl(String shop) {
        if (shop != null && !shop.isEmpty()) {
            String shopName = shop.replace("https://", "").replace(".myshopify.com", "");
            return "https://" + shopName + ".myshopify.com/admin/api/" + API_VERSION + "/graphql.json";
        }
        return "";
    }

}

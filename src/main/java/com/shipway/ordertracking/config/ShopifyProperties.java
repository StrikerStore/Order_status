package com.shipway.ordertracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import com.shipway.ordertracking.config.ShopifyAccount;

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
     * Get Shopify account configuration by account code
     */
    public ShopifyAccount getAccountByCode(String accountCode) {
        if (accountCode == null || accountCode.isEmpty()) {
            return null;
        }
        return accounts.get(accountCode.toUpperCase());
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

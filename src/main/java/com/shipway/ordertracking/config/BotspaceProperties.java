package com.shipway.ordertracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "botspace")
public class BotspaceProperties {

    // Map of account codes to Botspace account configurations
    private Map<String, BotspaceAccount> accounts = new HashMap<>();

    // Global Botspace configuration
    private String url;
    private String key;
    private String endpoint;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public BotspaceProperties() {
    }

    public Map<String, BotspaceAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, BotspaceAccount> accounts) {
        this.accounts = accounts;
    }

    /**
     * Get Botspace account configuration by account code
     */
    public BotspaceAccount getAccountByCode(String accountCode) {
        if (accountCode == null || accountCode.isEmpty()) {
            return null;
        }
        BotspaceAccount account = accounts.get(accountCode.toUpperCase());
        return account;
    }
}

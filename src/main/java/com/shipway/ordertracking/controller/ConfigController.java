package com.shipway.ordertracking.controller;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.config.ShopifyAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

        private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

        @Autowired
        private BotspaceProperties botspaceProperties;

        @Autowired
        private ShopifyProperties shopifyProperties;

        /**
         * Get Botspace configuration for a specific account code
         */
        @GetMapping("/botspace/{accountCode}")
        public ResponseEntity<Map<String, Object>> getBotspaceConfig(
                        @PathVariable String accountCode) {

                BotspaceAccount account = botspaceProperties.getAccountByCode(accountCode);

                if (account == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(Map.of(
                                                        "error", "Botspace account configuration not found",
                                                        "accountCode", accountCode,
                                                        "message", "No Botspace configuration found for account code: "
                                                                        + accountCode));
                }

                Map<String, Object> config = new HashMap<>();
                config.put("accountCode", accountCode.toUpperCase());
                config.put("baseUrl", account.getUrl());
                config.put("endpoint", account.getEndpoint());
                config.put("apiUrl", account.getApiUrl()); // Full URL (baseUrl + endpoint)
                config.put("hasApiKey", account.getKey() != null && !account.getKey().isEmpty());
                config.put("templates", Map.of(
                                "inTransit",
                                account.getInTransitTemplateId() != null ? account.getInTransitTemplateId()
                                                : "Not configured",
                                "outForDelivery",
                                account.getOutForDeliveryTemplateId() != null ? account.getOutForDeliveryTemplateId()
                                                : "Not configured",
                                "delivered",
                                account.getDeliveredTemplateId() != null ? account.getDeliveredTemplateId()
                                                : "Not configured",
                                "orderCreated",
                                account.getOrderCreatedTemplateId() != null ? account.getOrderCreatedTemplateId()
                                                : "Not configured",
                                "shopifyFulfillment",
                                account.getShopifyFulfillmentTemplateId() != null
                                                ? account.getShopifyFulfillmentTemplateId()
                                                : "Not configured",
                                "abandonedCart",
                                account.getAbandonedCartTemplateId() != null ? account.getAbandonedCartTemplateId()
                                                : "Not configured"));

                return ResponseEntity.ok(config);
        }

        /**
         * Get Shopify configuration for a specific account code
         */
        @GetMapping("/shopify/{accountCode}")
        public ResponseEntity<Map<String, Object>> getShopifyConfig(
                        @PathVariable String accountCode) {

                ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);

                if (account == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(Map.of(
                                                        "error", "Shopify account configuration not found",
                                                        "accountCode", accountCode,
                                                        "message", "No Shopify configuration found for account code: "
                                                                        + accountCode));
                }

                String apiUrl = account.getApiUrl();
                String graphQLUrl = account.getGraphQLUrl();

                Map<String, Object> config = new HashMap<>();
                config.put("accountCode", accountCode.toUpperCase());
                config.put("shop", account.getShop());
                config.put("restApiUrl", apiUrl);
                config.put("graphQLUrl", graphQLUrl);
                config.put("hasAccessToken", account.getAccessToken() != null && !account.getAccessToken().isEmpty());

                return ResponseEntity.ok(config);
        }

        /**
         * List all configured account codes
         */
        @GetMapping("/accounts")
        public ResponseEntity<Map<String, Object>> listAllAccounts() {
                Map<String, Object> response = new HashMap<>();

                response.put("botspaceAccounts", botspaceProperties.getAccounts().keySet());
                response.put("shopifyAccounts", shopifyProperties.getAccounts().keySet());
                response.put("totalBotspaceAccounts", botspaceProperties.getAccounts().size());
                response.put("totalShopifyAccounts", shopifyProperties.getAccounts().size());

                return ResponseEntity.ok(response);
        }
}

package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.dto.ShopifyOrderCreatedWebhook;
import com.shipway.ordertracking.config.ShopifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.config.ShopifyAccount;

@Service
public class OrderCreatedFlowService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedFlowService.class);

    @Autowired
    private BotspaceService botspaceService;

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private ShopifyProperties shopifyProperties;

    @Autowired
    private BotspaceProperties botspaceProperties;

    /**
     * Process Shopify order created webhook
     * Sends notification to customer when order is created using Botspace template
     */
    public boolean processShopifyOrderCreated(ShopifyOrderCreatedWebhook webhook, String shopDomain) {
        log.info("Processing Shopify order created flow for order: {}", webhook.getName());

        // Extract account code from shop domain
        String accountCode = extractAccountCodeFromShopDomain(shopDomain);
        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Cannot determine account code from shop domain: {}", shopDomain);
            return false;
        }

        // Get order name (e.g., "#1001")
        String orderName = webhook.getName();
        if (orderName == null || orderName.isEmpty()) {
            log.warn("Order name is missing in Shopify webhook");
            return false;
        }

        // Get customer phone number (prefer shipping address phone, fallback to order
        // phone or customer phone)
        String customerPhone = getCustomerPhone(webhook);
        if (customerPhone == null || customerPhone.isEmpty()) {
            log.warn("Customer phone is missing for order: {}", orderName);
            return false;
        }

        // Get template ID for this account
        String templateId = getTemplateIdForAccount(accountCode);
        if (templateId == null || templateId.isEmpty()) {
            log.warn("Template ID not configured for account code: {} (order: {})", accountCode, orderName);
            return false;
        }

        // Format phone number (add +91 prefix if needed)
        String formattedPhone = formatPhoneNumber(customerPhone);

        // Build template variables
        List<String> variables = buildTemplateVariables(webhook);

        // Create Botspace request
        BotspaceMessageRequest request = new BotspaceMessageRequest();
        request.setPhone(formattedPhone);
        request.setTemplateId(templateId);
        request.setVariables(variables);

        // Send template message via Botspace
        boolean sent = botspaceService.sendTemplateMessage(accountCode, request, orderName);

        if (sent) {
            log.info("✅ Order created notification sent successfully for order: {} to phone: {}",
                    orderName, formattedPhone);
        } else {
            log.error("❌ Failed to send order created notification for order: {} to phone: {}",
                    orderName, formattedPhone);
        }

        return sent;
    }

    /**
     * Extract account code from Shopify shop domain
     * Example: shop-stri.myshopify.com -> STRI
     * You can customize this logic based on your shop naming convention
     */
    private String extractAccountCodeFromShopDomain(String shopDomain) {
        if (shopDomain == null || shopDomain.isEmpty()) {
            return null;
        }

        // Remove .myshopify.com if present
        String shopName = shopDomain.replace(".myshopify.com", "").replace("https://", "").replace("http://", "");

        // Try to find matching account in configuration
        // Check if any configured account's shop matches
        for (String accountCode : shopifyProperties.getAccounts().keySet()) {
            ShopifyAccount account = shopifyProperties.getAccountByCode(accountCode);
            if (account != null && account.getShop() != null) {
                String configuredShop = account.getShop().replace(".myshopify.com", "");
                if (configuredShop.equalsIgnoreCase(shopName)) {
                    return accountCode;
                }
            }
        }

        // If no match found, try to extract from shop name (e.g., shop-stri -> STRI)
        // Customize this based on your naming convention
        if (shopName.contains("-")) {
            String[] parts = shopName.split("-");
            if (parts.length > 1) {
                return parts[parts.length - 1].toUpperCase();
            }
        }

        // Default: use shop name as account code (uppercase)
        return shopName.toUpperCase();
    }

    /**
     * Get customer phone from webhook (prefer shipping address, fallback to order
     * phone or customer phone)
     */
    private String getCustomerPhone(ShopifyOrderCreatedWebhook webhook) {
        // Try shipping address phone first
        if (webhook.getShippingAddress() != null &&
                webhook.getShippingAddress().getPhone() != null &&
                !webhook.getShippingAddress().getPhone().isEmpty()) {
            return webhook.getShippingAddress().getPhone();
        }

        // Try order phone
        if (webhook.getPhone() != null && !webhook.getPhone().isEmpty()) {
            return webhook.getPhone();
        }

        // Try customer phone
        if (webhook.getCustomer() != null &&
                webhook.getCustomer().getPhone() != null &&
                !webhook.getCustomer().getPhone().isEmpty()) {
            return webhook.getCustomer().getPhone();
        }

        return null;
    }

    /**
     * Format phone number with country code
     */
    private String formatPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }

        // Remove any spaces or dashes
        String cleaned = phone.replaceAll("[\\s-]", "");

        // Add +91 prefix if not present (assuming Indian numbers)
        if (!cleaned.startsWith("+91") && !cleaned.startsWith("91")) {
            if (cleaned.startsWith("0")) {
                cleaned = cleaned.substring(1);
            }
            return "+91" + cleaned;
        }

        // Ensure + prefix
        if (cleaned.startsWith("91") && !cleaned.startsWith("+91")) {
            return "+" + cleaned;
        }

        return cleaned;
    }

    /**
     * Build template variables array
     * Order of variables should match the template variable order in Botspace
     */
    private List<String> buildTemplateVariables(ShopifyOrderCreatedWebhook webhook) {
        List<String> variables = new ArrayList<>();

        // Variable 1: Customer first name (prefer shipping address, fallback to
        // customer)
        String firstName = "";
        if (webhook.getShippingAddress() != null && webhook.getShippingAddress().getFirstName() != null) {
            firstName = webhook.getShippingAddress().getFirstName();
        } else if (webhook.getCustomer() != null && webhook.getCustomer().getFirstName() != null) {
            firstName = webhook.getCustomer().getFirstName();
        }
        variables.add(firstName);

        // Variable 2: Order name/number
        String orderName = webhook.getName() != null ? webhook.getName() : "";
        variables.add(orderName);

        // Add more variables as needed based on your template

        return variables;
    }

    /**
     * Get template ID for order created status based on account code
     */
    private String getTemplateIdForAccount(String accountCode) {
        // Get Botspace account config
        BotspaceAccount botspaceAccount = botspaceProperties.getAccountByCode(accountCode);

        if (botspaceAccount != null && botspaceAccount.getOrderCreatedTemplateId() != null) {
            return botspaceAccount.getOrderCreatedTemplateId();
        }

        log.warn("Order created template ID not found for account code: {}", accountCode);
        return null;
    }

}

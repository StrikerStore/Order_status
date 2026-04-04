package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.dto.ShopifyOrderCreatedWebhook;
import com.shipway.ordertracking.config.ShopifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.util.PhoneNumberUtil;

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

    /** When set (e.g. for local testing), order-created notifications go to this number instead of the customer. */
    @Value("${order.created.test.phone:}")
    private String orderCreatedTestPhone;

    /**
     * Process Shopify order created webhook
     * Sends notification to customer when order is created using Botspace template
     */
    public boolean processShopifyOrderCreated(ShopifyOrderCreatedWebhook webhook, String shopDomain) {
        log.info("Processing Shopify order created flow for order: {}", webhook.getName());

        // Resolve brand key (e.g. STRIKER STORE / DRIBBLE STORE) from shop domain to match shopify.accounts / botspace.accounts
        String brandName = extractBrandNameFromShopDomain(shopDomain);
        if (brandName == null || brandName.isEmpty()) {
            log.warn("Cannot determine brand name from shop domain: {}", shopDomain);
            return false;
        }

        // Get order name (e.g., "#1001") for tracking in customer_message_tracking
        String orderName = webhook.getName() != null ? webhook.getName() : "";
        if (orderName.isEmpty()) {
            log.warn("Order name is missing in Shopify webhook");
            return false;
        }

        // Get customer phone number (prefer shipping address phone, fallback to order
        // phone or customer phone). Override with order.created.test.phone when set (e.g. local testing).
        String customerPhone = (orderCreatedTestPhone != null && !orderCreatedTestPhone.isEmpty())
                ? orderCreatedTestPhone
                : getCustomerPhone(webhook);
        if (customerPhone == null || customerPhone.isEmpty()) {
            log.warn("Customer phone is missing for order: {}", orderName);
            return false;
        }
        if (orderCreatedTestPhone != null && !orderCreatedTestPhone.isEmpty()) {
            log.info("Using order-created test phone override for order: {}", orderName);
        }

        // Get template ID for this brand
        String templateId = getTemplateIdForBrand(brandName);
        if (templateId == null || templateId.isEmpty()) {
            log.warn("Template ID not configured for brand: {} (order: {})", brandName, orderName);
            return false;
        }

        // Format phone number (add +91 prefix if needed)
        String formattedPhone = PhoneNumberUtil.formatPhoneNumber(customerPhone);

        // Build template variables
        List<String> variables = buildTemplateVariables(webhook);

        // Create Botspace request
        BotspaceMessageRequest request = new BotspaceMessageRequest();
        request.setPhone(formattedPhone);
        request.setTemplateId(templateId);
        request.setVariables(variables);

        // Send template message via Botspace (order_id in table = order name)
        boolean sent = botspaceService.sendTemplateMessage(brandName, request, orderName,
                "sent_orderCreated",
                "failed_orderCreated");

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
     * Resolve configured brand key from Shopify shop domain (e.g. seq5t1-mz.myshopify.com → STRIKER STORE).
     */
    private String extractBrandNameFromShopDomain(String shopDomain) {
        if (shopDomain == null || shopDomain.isEmpty()) {
            return null;
        }

        // Remove .myshopify.com if present
        String shopName = shopDomain.replace(".myshopify.com", "").replace("https://", "").replace("http://", "");

        // Match shop domain against configured Shopify accounts
        for (String key : shopifyProperties.getAccounts().keySet()) {
            ShopifyAccount account = shopifyProperties.getAccountByCode(key);
            if (account != null && account.getShop() != null) {
                String configuredShop = account.getShop().replace(".myshopify.com", "");
                if (configuredShop.equalsIgnoreCase(shopName)) {
                    return key;
                }
            }
        }

        // If no match found, try to extract from shop name (last segment after '-', uppercased)
        if (shopName.contains("-")) {
            String[] parts = shopName.split("-");
            if (parts.length > 1) {
                return parts[parts.length - 1].toUpperCase();
            }
        }

        // Default: use shop name as brand key (uppercase)
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

        return variables;
    }

    private String getTemplateIdForBrand(String brandName) {
        BotspaceAccount botspaceAccount = botspaceProperties.getAccountByCode(brandName);

        if (botspaceAccount != null && botspaceAccount.getOrderCreatedTemplateId() != null) {
            return botspaceAccount.getOrderCreatedTemplateId();
        }

        log.warn("Order created template ID not found for brand: {}", brandName);
        return null;
    }

}

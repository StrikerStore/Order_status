package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.dto.FasterrAbandonedCartWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.shipway.ordertracking.config.BotspaceAccount;

@Service
public class AbandonedCartFlowService {

    private static final Logger log = LoggerFactory.getLogger(AbandonedCartFlowService.class);

    @Autowired
    private BotspaceService botspaceService;

    @Autowired
    private com.shipway.ordertracking.config.BotspaceProperties botspaceProperties;

    /**
     * Process abandoned cart webhook and schedule delayed Botspace notification
     */
    public boolean processAbandonedCart(FasterrAbandonedCartWebhook webhook) {
        log.info("Processing abandoned cart webhook for phone: {}", webhook.getPhone());

        // Validate required fields
        String customerPhone = webhook.getPhone();
        if (customerPhone == null || customerPhone.isEmpty()) {
            log.warn("Customer phone is missing in abandoned cart webhook");
            return false;
        }

        // Extract account code from landing_page_url or use default
        String accountCode = extractAccountCode(webhook);
        log.info("Extracted account code: {} for abandoned cart", accountCode);

        // Build template variables
        List<String> templateVariables = buildTemplateVariables(webhook);

        // Extract cart token for tracking
        String cartToken = null;
        if (webhook.getCustomAttributes() != null) {
            cartToken = webhook.getCustomAttributes().getShopifyCartToken();
        }

        // Schedule async task with 1-hour delay using CompletableFuture.delayedExecutor
        String finalCartToken = cartToken;
        CompletableFuture.runAsync(() -> {
            try {
                sendAbandonedCartNotification(accountCode, customerPhone, templateVariables, finalCartToken);
            } catch (Exception e) {
                log.error("Error in abandoned cart notification task for phone: {}", customerPhone, e);
            }
        }, CompletableFuture.delayedExecutor(1, TimeUnit.HOURS));

        log.info("Abandoned cart notification scheduled for phone: {} (account: {}), will send in 1 hour",
                customerPhone, accountCode);
        return true;
    }

    /**
     * Send abandoned cart notification via Botspace (called after 1-hour delay)
     */
    @Async
    private void sendAbandonedCartNotification(String accountCode, String phone, List<String> templateVariables,
            String cartToken) {
        try {
            log.info("Sending abandoned cart notification for phone: {} (account: {})", phone, accountCode);

            // Get template ID for this account
            String templateId = getTemplateIdForAccount(accountCode);
            if (templateId == null || templateId.isEmpty()) {
                log.warn("Template ID not configured for account code: {} (phone: {})", accountCode, phone);
                return;
            }

            // Format phone number
            String formattedPhone = formatPhoneNumber(phone);

            // Create Botspace request
            BotspaceMessageRequest request = new BotspaceMessageRequest();
            request.setPhone(formattedPhone);
            request.setTemplateId(templateId);
            request.setVariables(templateVariables);

            // Send template message via Botspace
            // Send template message via Botspace
            boolean sent = botspaceService.sendTemplateMessage(accountCode, request, cartToken,
                    "sent_abandonedCart", "failed_abandonedCart");

            if (sent) {
                log.info("✅ Abandoned cart notification sent successfully for phone: {} (account: {})",
                        formattedPhone, accountCode);
            } else {
                log.error("❌ Failed to send abandoned cart notification for phone: {} (account: {})",
                        formattedPhone, accountCode);
            }

        } catch (Exception e) {
            log.error("❌ Error sending abandoned cart notification for phone {}: {}",
                    phone, e.getMessage(), e);
        }
    }

    /**
     * Extract account code from landing_page_url domain or use default
     */
    private String extractAccountCode(FasterrAbandonedCartWebhook webhook) {
        if (webhook.getCustomAttributes() != null &&
                webhook.getCustomAttributes().getLandingPageUrl() != null) {

            String landingPageUrl = webhook.getCustomAttributes().getLandingPageUrl();
            try {
                // Extract domain from URL (e.g., "thestrikerstore.com" -> "thestrikerstore")
                java.net.URL url = new java.net.URL(landingPageUrl);
                String host = url.getHost();

                // Remove www. prefix if present
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }

                // Extract subdomain/domain name (before first dot)
                int firstDotIndex = host.indexOf('.');
                if (firstDotIndex > 0) {
                    String accountCode = host.substring(0, firstDotIndex);
                    log.debug("Extracted account code '{}' from landing page URL: {}", accountCode, landingPageUrl);
                    return accountCode.toUpperCase();
                }
            } catch (Exception e) {
                log.warn("Could not parse landing page URL for account code extraction: {}", landingPageUrl, e);
            }
        }

        // Default account code if extraction fails
        log.debug("Using default account code 'DEFAULT'");
        return "DEFAULT";
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
    private List<String> buildTemplateVariables(FasterrAbandonedCartWebhook webhook) {
        List<String> variables = new ArrayList<>();

        // Variable 1: Customer first name
        String firstName = webhook.getFirstName() != null ? webhook.getFirstName() : "";
        variables.add(firstName);

        // Variable 2: Cart recovery URL (if available)
        if (webhook.getCustomAttributes() != null &&
                webhook.getCustomAttributes().getLandingPageUrl() != null) {
            String landingPageUrl = webhook.getCustomAttributes().getLandingPageUrl();
            variables.add(landingPageUrl);
        } else {
            variables.add(""); // Empty string if no URL
        }

        // Add more variables as needed based on your template

        return variables;
    }

    /**
     * Get template ID for abandoned cart status based on account code
     */
    private String getTemplateIdForAccount(String accountCode) {
        // Get Botspace account config
        BotspaceAccount botspaceAccount = botspaceProperties.getAccountByCode(accountCode);

        if (botspaceAccount != null && botspaceAccount.getAbandonedCartTemplateId() != null) {
            return botspaceAccount.getAbandonedCartTemplateId();
        }

        log.warn("Abandoned cart template ID not found for account code: {}", accountCode);
        return null;
    }
}

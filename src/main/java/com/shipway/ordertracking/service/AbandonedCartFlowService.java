package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.dto.FasterrAbandonedCartWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.util.BrandAccountKey;
import com.shipway.ordertracking.util.PhoneNumberUtil;

@Service
public class AbandonedCartFlowService {

    private static final Logger log = LoggerFactory.getLogger(AbandonedCartFlowService.class);

    @Autowired
    private BotspaceService botspaceService;

    @Autowired
    private com.shipway.ordertracking.config.BotspaceProperties botspaceProperties;

    @Autowired
    private com.shipway.ordertracking.service.ShopifyService shopifyService;

    /** When set (e.g. for local testing), abandoned cart notifications go to this number instead of the customer. */
    @Value("${abandoned.cart.test.phone:}")
    private String abandonedCartTestPhone;

    /**
     * Process abandoned cart webhook and schedule delayed Botspace notification
     * Payload has cart data under attributes (prod shape).
     */
    public boolean processAbandonedCart(FasterrAbandonedCartWebhook webhook) {
        if (webhook == null || webhook.getAttributes() == null) {
            log.warn("Abandoned cart webhook or attributes is null");
            return false;
        }
        FasterrAbandonedCartWebhook.Attributes attrs = webhook.getAttributes();

        String customerPhone = (abandonedCartTestPhone != null && !abandonedCartTestPhone.isEmpty())
                ? abandonedCartTestPhone
                : getPhoneFromAttributes(attrs);
        log.info("Processing abandoned cart webhook for phone: {}", customerPhone);

        if (customerPhone == null || customerPhone.isEmpty()) {
            log.warn("Customer phone is missing in abandoned cart webhook");
            return false;
        }
        if (abandonedCartTestPhone != null && !abandonedCartTestPhone.isEmpty()) {
            log.info("Using abandoned-cart test phone override");
        }

        // Brand key (e.g. STRIKER STORE / DRIBBLE STORE) from landing_page_url or checkout_url
        String brandName = extractBrandNameFromWebhook(webhook);
        log.info("Resolved brand name: {} for abandoned cart", brandName);

        // Build template variables [first_name, recovery_url (onlineStoreUrl or checkout_url), product_id]
        List<String> templateVariables = buildTemplateVariables(webhook, brandName);

        // First item image URL for mediaVariable and cards (Botspace template expects these)
        String firstItemImgUrl = getFirstItemImgUrl(attrs);

        // Cart ID for tracking (prod uses cart_id, not shopifyCartToken)
        String cartToken = attrs.getCartId();

        // Schedule async task with 1-minute delay
        String finalCartToken = cartToken;
        String finalFirstItemImgUrl = firstItemImgUrl;
        CompletableFuture.runAsync(() -> {
            try {
                sendAbandonedCartNotification(brandName, customerPhone, templateVariables, finalCartToken, finalFirstItemImgUrl);
            } catch (Exception e) {
                log.error("Error in abandoned cart notification task for phone: {}", customerPhone, e);
            }
        }, CompletableFuture.delayedExecutor(1, TimeUnit.MINUTES));

        log.info("Abandoned cart notification scheduled for phone: {} (brand: {}), will send in 1 minute",
                customerPhone, brandName);
        return true;
    }

    /** Phone: attributes.phone_number, else shipping_address.phone, else billing_address.phone */
    private String getPhoneFromAttributes(FasterrAbandonedCartWebhook.Attributes attrs) {
        if (attrs.getPhoneNumber() != null && !attrs.getPhoneNumber().isEmpty()) {
            return attrs.getPhoneNumber();
        }
        if (attrs.getShippingAddress() != null && attrs.getShippingAddress().getPhone() != null) {
            return attrs.getShippingAddress().getPhone();
        }
        if (attrs.getBillingAddress() != null && attrs.getBillingAddress().getPhone() != null) {
            return attrs.getBillingAddress().getPhone();
        }
        return null;
    }

    /**
     * Send abandoned cart notification via Botspace (called after 1-minute delay).
     * Matches Botspace API: variables [first_name, recovery_url, product_id], mediaVariable and cards from first item img_url.
     */
    @Async
    private void sendAbandonedCartNotification(String brandName, String phone, List<String> templateVariables,
            String cartToken, String firstItemImgUrl) {
        try {
            log.info("Sending abandoned cart notification for phone: {} (brand: {})", phone, brandName);

            String templateId = getTemplateIdForBrand(brandName);
            if (templateId == null || templateId.isEmpty()) {
                log.warn("Template ID not configured for brand: {} (phone: {})", brandName, phone);
                return;
            }

            String formattedPhone = PhoneNumberUtil.formatPhoneNumber(phone);

            BotspaceMessageRequest request = new BotspaceMessageRequest();
            request.setPhone(formattedPhone);
            request.setTemplateId(templateId);
            request.setVariables(templateVariables);

            if (firstItemImgUrl != null && !firstItemImgUrl.isEmpty()) {
                request.setMediaVariable(firstItemImgUrl);
                request.setCards(List.of(new BotspaceMessageRequest.Card(firstItemImgUrl)));
            }

            boolean sent = botspaceService.sendTemplateMessage(brandName, request, cartToken,
                    "sent_abandonedCart", "failed_abandonedCart");

            if (sent) {
                log.info("✅ Abandoned cart notification sent successfully for phone: {} (brand: {})",
                        formattedPhone, brandName);
            } else {
                log.error("❌ Failed to send abandoned cart notification for phone: {} (brand: {})",
                        formattedPhone, brandName);
            }

        } catch (Exception e) {
            log.error("❌ Error sending abandoned cart notification for phone {}: {}",
                    phone, e.getMessage(), e);
        }
    }

    /**
     * Resolve brand key from {@code custom_attributes.landing_page_url} or {@code checkout_url} host.
     */
    private String extractBrandNameFromWebhook(FasterrAbandonedCartWebhook webhook) {
        String landingPageUrl = null;
        if (webhook.getAttributes() != null && webhook.getAttributes().getCustomAttributes() != null) {
            landingPageUrl = webhook.getAttributes().getCustomAttributes().getLandingPageUrl();
        }
        if (landingPageUrl == null || landingPageUrl.isEmpty()) {
            if (webhook.getAttributes() != null && webhook.getAttributes().getCheckoutUrl() != null) {
                landingPageUrl = webhook.getAttributes().getCheckoutUrl();
            }
        }
        if (landingPageUrl != null && !landingPageUrl.isEmpty()) {
            try {
                java.net.URL url = new java.net.URL(landingPageUrl);
                String host = url.getHost();
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }
                int firstDotIndex = host.indexOf('.');
                if (firstDotIndex > 0) {
                    String domainPart = host.substring(0, firstDotIndex);
                    log.debug("Extracted domain part '{}' from URL: {}", domainPart, landingPageUrl);
                    String lower = domainPart.toLowerCase();
                    if ("thestrikerstore".equals(lower)) {
                        return BrandAccountKey.STRIKER_STORE;
                    }
                    if ("thedribblestore".equals(lower)) {
                        return BrandAccountKey.DRIBBLE_STORE;
                    }
                    return domainPart.toUpperCase();
                }
            } catch (Exception e) {
                log.warn("Could not parse URL for brand name extraction: {}", landingPageUrl, e);
            }
        }
        log.debug("Using default brand name 'DEFAULT'");
        return "DEFAULT";
    }

    /**
     * Build template variables to match Botspace abandoned_cart template:
     * [0] first_name, [1] product onlineStoreUrl (from Shopify GraphQL) or checkout_url fallback, [2] first item product_id
     */
    private List<String> buildTemplateVariables(FasterrAbandonedCartWebhook webhook, String brandName) {
        List<String> variables = new ArrayList<>();
        FasterrAbandonedCartWebhook.Attributes attrs = webhook.getAttributes();
        String firstName = attrs != null && attrs.getFirstName() != null ? attrs.getFirstName() : "";
        variables.add(firstName);

        // Variable 2: product onlineStoreUrl from Shopify (GraphQL GetProductById) only; no fallback
        String recoveryUrl = "";
        if (attrs != null && attrs.getItems() != null && !attrs.getItems().isEmpty()) {
            Long productId = attrs.getItems().get(0).getProductId();
            if (productId != null && brandName != null && !brandName.isEmpty()) {
                String onlineStoreUrl = shopifyService.getProductOnlineStoreUrl(brandName, productId);
                if (onlineStoreUrl != null && !onlineStoreUrl.isEmpty()) {
                    recoveryUrl = onlineStoreUrl;
                }
            }
        }
        variables.add(recoveryUrl);

        // Variable 3: first item product_id (as string)
        String productIdStr = "";
        if (attrs != null && attrs.getItems() != null && !attrs.getItems().isEmpty()) {
            Long pid = attrs.getItems().get(0).getProductId();
            if (pid != null) {
                productIdStr = String.valueOf(pid);
            }
        }
        variables.add(productIdStr);
        return variables;
    }

    /** First item img_url from attributes.items for mediaVariable and cards */
    private String getFirstItemImgUrl(FasterrAbandonedCartWebhook.Attributes attrs) {
        if (attrs == null || attrs.getItems() == null || attrs.getItems().isEmpty()) {
            return null;
        }
        return attrs.getItems().get(0).getImgUrl();
    }

    private String getTemplateIdForBrand(String brandName) {
        BotspaceAccount botspaceAccount = botspaceProperties.getAccountByCode(brandName);

        if (botspaceAccount != null && botspaceAccount.getAbandonedCartTemplateId() != null) {
            return botspaceAccount.getAbandonedCartTemplateId();
        }

        log.warn("Abandoned cart template ID not found for brand: {}", brandName);
        return null;
    }
}

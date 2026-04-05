package com.shipway.ordertracking.service;

import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.repository.OrderPhoneProjection;
import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sends a Botspace follow-up message to customers who received the "delivered" message yesterday.
 * Runs daily (e.g. 9 AM); uses DB join (customer_info + customer_message_tracking) for order_id, shipping_phone, account_code.
 */
@Service
public class PostDeliveredFollowUpService {

    private static final Logger log = LoggerFactory.getLogger(PostDeliveredFollowUpService.class);

    private static final List<String> FOLLOW_UP_STATUSES = Arrays.asList("sent_postDeliveredFollowUp", "failed_postDeliveredFollowUp");

    @Autowired
    private CustomerMessageTrackingService customerMessageTrackingService;

    @Autowired
    private BotspaceService botspaceService;

    @Autowired
    private BotspaceProperties botspaceProperties;

    /** WhatsApp community invite URL for post_delivered_followup template (variables + mediaVariable + cards). */
    @Value("${post.delivered.followup.community.url:https://chat.whatsapp.com/LN27qhbJXoNEKVkjmaSg0H}")
    private String communityUrl;

    /** When set, post-delivered follow-up messages go to this number only (for testing). */
    @Value("${post.delivered.followup.test.phone:}")
    private String postDeliveredFollowUpTestPhone;

    /** Runs daily at 9 AM server time. Override with post.delivered.followup.cron if needed. */
    @Scheduled(cron = "${post.delivered.followup.cron:0 0 9 * * *}")
    public void sendFollowUpToYesterdayDelivered() {
        log.info("Post-delivered follow-up: finding orders (order_id, shipping_phone) from DB for sent_delivered yesterday");
        List<OrderPhoneProjection> rows = customerMessageTrackingService.findOrderIdAndPhoneForSentDeliveredYesterday();
        if (rows.isEmpty()) {
            log.info("Post-delivered follow-up: no orders found");
            return;
        }
        log.info("Post-delivered follow-up: found {} order(s) to process", rows.size());
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (OrderPhoneProjection row : rows) {
            String orderId = row.getOrderId();
            String trackingAcct = row.getAccountCode() != null ? row.getAccountCode().trim() : "";
            String brandKey = row.getBrandName() != null ? row.getBrandName().trim() : "";
            String dedupeBrand = !brandKey.isEmpty() ? brandKey : trackingAcct;
            if (customerMessageTrackingService.hasAnyStatus(orderId, dedupeBrand, FOLLOW_UP_STATUSES)) {
                log.debug("Post-delivered follow-up: order {} already has follow-up status, skipping", orderId);
                skipped++;
                continue;
            }
            String phone = (postDeliveredFollowUpTestPhone != null && !postDeliveredFollowUpTestPhone.isEmpty())
                    ? postDeliveredFollowUpTestPhone
                    : row.getShippingPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("Post-delivered follow-up: no shipping_phone for order {} (tracking account: {}), skipping", orderId,
                        trackingAcct);
                failed++;
                continue;
            }
            if (postDeliveredFollowUpTestPhone != null && !postDeliveredFollowUpTestPhone.isEmpty()) {
                log.debug("Post-delivered follow-up: using test phone override for order {}", orderId);
            }
            String formattedPhone = PhoneNumberUtil.formatPhoneNumber(phone);
            if (formattedPhone.isEmpty()) {
                log.warn("Post-delivered follow-up: invalid phone for order {} (tracking account: {}), skipping", orderId,
                        trackingAcct);
                failed++;
                continue;
            }
            String botspaceKey = !brandKey.isEmpty() ? brandKey : trackingAcct;
            String templateId = getTemplateIdForAccount(botspaceKey);
            if (templateId == null || templateId.isEmpty()) {
                log.warn("Post-delivered follow-up: no template for Botspace key {}, skipping order {}", botspaceKey, orderId);
                failed++;
                continue;
            }
            String url = (communityUrl != null && !communityUrl.isEmpty()) ? communityUrl : "https://chat.whatsapp.com/LN27qhbJXoNEKVkjmaSg0H";
            List<String> variables = new ArrayList<>();
            variables.add(url);
            BotspaceMessageRequest request = new BotspaceMessageRequest();
            request.setPhone(formattedPhone);
            request.setTemplateId(templateId);
            request.setVariables(variables);
            request.setMediaVariable(url);
            request.setCards(List.of(new BotspaceMessageRequest.Card(url)));
            boolean ok = botspaceService.sendTemplateMessage(botspaceKey, request, orderId, "sent_postDeliveredFollowUp",
                    "failed_postDeliveredFollowUp",
                    trackingAcct.isEmpty() ? null : trackingAcct,
                    brandKey.isEmpty() ? null : brandKey);
            if (ok) {
                sent++;
                log.info("Post-delivered follow-up sent for order {} (Botspace key: {})", orderId, botspaceKey);
            } else {
                failed++;
            }
        }
        log.info("Post-delivered follow-up done: sent={}, skipped={}, failed={}", sent, skipped, failed);
    }

    private String getTemplateIdForAccount(String botspaceAccountKey) {
        BotspaceAccount account = botspaceProperties.getAccountByCode(botspaceAccountKey);
        return account != null ? account.getPostDeliveredFollowUpTemplateId() : null;
    }
}

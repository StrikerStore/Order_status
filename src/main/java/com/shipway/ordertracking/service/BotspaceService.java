package com.shipway.ordertracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.dto.BotspaceMessageResponse;
import com.shipway.ordertracking.dto.ClaimioTrackingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class BotspaceService {

    private static final Logger log = LoggerFactory.getLogger(BotspaceService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private BotspaceProperties botspaceProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${botspace.test.phone:}")
    private String testPhoneNumber;

    @Value("${backend.claimio.url:}")
    private String claimioUrl;

    @Value("${backend.claimio.username:}")
    private String claimioUsername;

    @Value("${backend.claimio.password:}")
    private String claimioPassword;

    /**
     * Send template message to customer via Botspace and track it in Claimio
     * backend using default status "sent"/"failed"
     * 
     * @param accountCode Account code to identify which Botspace account to use
     * @param request     BotspaceMessageRequest with templateId and variables
     * @param orderId     Order ID for tracking purposes
     * @return true if message sent successfully, false otherwise
     */
    public boolean sendTemplateMessage(String accountCode, BotspaceMessageRequest request, String orderId) {
        return sendTemplateMessage(accountCode, request, orderId, "sent", "failed");
    }

    /**
     * Send template message to customer via Botspace and track it in Claimio
     * backend using custom status strings
     * 
     * @param accountCode   Account code to identify which Botspace account to use
     * @param request       BotspaceMessageRequest with templateId and variables
     * @param orderId       Order ID for tracking purposes
     * @param successStatus Status string to send if message sent successfully
     * @param failureStatus Status string to send if message failed
     * @return true if message sent successfully, false otherwise
     */
    public boolean sendTemplateMessage(String accountCode, BotspaceMessageRequest request, String orderId,
            String successStatus, String failureStatus) {
        boolean sent = sendTemplateMessage(accountCode, request);

        if (orderId != null && !orderId.isEmpty()) {
            String status = sent ? successStatus : failureStatus;
            // Run tracking update asynchronously to not block the main flow?
            // For now, running synchronously but safely caught to not affect return value
            try {
                sendTrackingUpdate(orderId, accountCode, status);
            } catch (Exception e) {
                log.error("Failed to send tracking update for order {}: {}", orderId, e.getMessage());
            }
        }

        return sent;
    }

    /**
     * Send tracking update to Claimio backend
     */
    private void sendTrackingUpdate(String orderId, String accountCode, String status) {
        if (claimioUrl == null || claimioUrl.isEmpty()) {
            log.warn("Claimio backend URL is not configured, skipping tracking update");
            return;
        }

        try {
            String apiUrl = claimioUrl + "/api/orders/message-tracking";

            ClaimioTrackingRequest trackingRequest = new ClaimioTrackingRequest(orderId, accountCode, status);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(claimioUsername, claimioPassword);

            HttpEntity<ClaimioTrackingRequest> entity = new HttpEntity<>(trackingRequest, headers);

            log.debug("Sending tracking update for order {} to {}, status: {}", orderId, apiUrl, status);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        apiUrl,
                        HttpMethod.POST,
                        entity,
                        String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Tracking update sent for order {} (status: {})", orderId, status);
                } else {
                    log.warn("❌ Tracking update failed for order {}: Status {}", orderId, response.getStatusCode());
                }
            } catch (RestClientException e) {
                log.error("❌ Error sending tracking update: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Unexpected error in sendTrackingUpdate: {}", e.getMessage());
        }
    }

    /**
     * Send template message to customer via Botspace
     * 
     * @param accountCode Account code to identify which Botspace account to use
     * @param request     BotspaceMessageRequest with templateId and variables
     * @return true if message sent successfully, false otherwise
     */
    public boolean sendTemplateMessage(String accountCode, BotspaceMessageRequest request) {
        if (request == null) {
            log.warn("Cannot send message: request is null");
            return false;
        }

        String phone = request.getPhone();
        if (phone == null || phone.isEmpty()) {
            log.warn("Cannot send message: phone number is empty");
            return false;
        }

        // Override phone number with test number if configured
        String originalPhone = phone;
        if (testPhoneNumber != null && !testPhoneNumber.trim().isEmpty()) {
            phone = testPhoneNumber.trim();
            log.info("🧪 TEST MODE: Overriding phone number from {} to test number: {}", originalPhone, phone);
        }

        String templateId = request.getTemplateId();
        if (templateId == null || templateId.isEmpty()) {
            log.warn("Cannot send message: templateId is empty");
            return false;
        }

        if (accountCode == null || accountCode.isEmpty()) {
            log.warn("Cannot send message: account code is empty");
            return false;
        }

        // Get Botspace account configuration for this account code
        BotspaceAccount account = botspaceProperties.getAccountByCode(accountCode);
        if (account == null) {
            log.warn("Botspace account configuration not found for account code: {}", accountCode);
            return false;
        }

        try {
            // Update request with potentially overridden phone number
            request.setPhone(phone);

            // Determine API URL (use global if account URL is missing)
            String baseUrl = account.getUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = botspaceProperties.getUrl();
            }

            String endpoint = account.getEndpoint();
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = botspaceProperties.getEndpoint();
            }

            String apiKey = account.getKey();
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = botspaceProperties.getKey();
            }

            // Build effective URL
            if (baseUrl != null) {
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
            } else {
                baseUrl = "";
            }

            if (endpoint != null) {
                if (endpoint.startsWith("/")) {
                    endpoint = endpoint.substring(1);
                }
            } else {
                endpoint = "";
            }

            String apiUrl = baseUrl + "/" + endpoint;

            // Botspace API expects apiKey as query parameter (Query Params: apiKey=...)
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(apiUrl);
            if (apiKey != null && !apiKey.isEmpty()) {
                urlBuilder.queryParam("apiKey", apiKey);
            }
            String finalApiUrl = urlBuilder.build().toUriString();
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<BotspaceMessageRequest> entity = new HttpEntity<>(request, headers);

            // Log JSON request body
            try {
                String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
                log.info("📤 Botspace API Request JSON (Account: {}):\n{}", accountCode, jsonRequest);
            } catch (Exception e) {
                log.warn("Failed to serialize Botspace request to JSON: {}", e.getMessage());
            }

            log.info("Sending template message to Botspace API: {} (account: {})", finalApiUrl, accountCode);
            log.debug("Template details - Phone: {}, TemplateId: {}, Variables: {}",
                    phone, templateId, request.getVariables());

            ResponseEntity<BotspaceMessageResponse> response = restTemplate.exchange(
                    finalApiUrl,
                    HttpMethod.POST,
                    entity,
                    BotspaceMessageResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                BotspaceMessageResponse responseBody = response.getBody();
                try {
                    String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(responseBody);
                    log.info("📥 Botspace API Response JSON (Account: {}):\n{}", accountCode, jsonResponse);
                } catch (Exception e) {
                    log.warn("Failed to serialize Botspace response to JSON: {}", e.getMessage());
                }
                if (responseBody.isAccepted()) {
                    String msgId = responseBody.getData() != null ? responseBody.getData().getId() : "unknown";
                    log.info(
                            "✅ Template message sent successfully to {} via Botspace. Account: {}, TemplateId: {}, MessageId: {}",
                            phone, accountCode, templateId, msgId);
                    return true;
                } else {
                    log.warn("⚠️ Botspace API did not accept message. Data status: {}",
                            responseBody.getData() != null ? responseBody.getData().getStatus() : "unknown");
                    return false;
                }
            } else {
                log.error("❌ Botspace API returned non-2xx status: {}", response.getStatusCode());
                return false;
            }

        } catch (RestClientException e) {
            log.error("❌ Error calling Botspace API for phone {} (account: {}): {}", phone, accountCode, e.getMessage(),
                    e);
            return false;
        } catch (Exception e) {
            log.error("❌ Unexpected error sending template message to Botspace (account: {}): {}", accountCode,
                    e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create HTTP headers. Botspace accepts apiKey as query param; some endpoints
     * may also require or prefer header.
     */
    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("apiKey", apiKey);
        }
        return headers;
    }
}

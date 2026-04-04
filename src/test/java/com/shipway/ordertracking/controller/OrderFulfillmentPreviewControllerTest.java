package com.shipway.ordertracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipway.ordertracking.dto.BulkFulfillFromTrackingRequest;
import com.shipway.ordertracking.dto.BulkFulfillFromTrackingResponse;
import com.shipway.ordertracking.dto.UnfulfilledShopifyPreviewResponse;
import com.shipway.ordertracking.service.OrderTrackingBulkFulfillmentService;
import com.shipway.ordertracking.service.UnfulfilledShopifyPreviewService;
import com.shipway.ordertracking.util.BrandAccountKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderFulfillmentPreviewController.class)
class OrderFulfillmentPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UnfulfilledShopifyPreviewService unfulfilledShopifyPreviewService;

    @MockBean
    private OrderTrackingBulkFulfillmentService orderTrackingBulkFulfillmentService;

    @Test
    void listUnfulfilled_defaultLimit_delegatesToService() throws Exception {
        UnfulfilledShopifyPreviewResponse body = new UnfulfilledShopifyPreviewResponse();
        when(unfulfilledShopifyPreviewService.buildPreview(isNull(), eq(200))).thenReturn(body);

        mockMvc.perform(get("/api/orders/unfulfilled-with-tracking-status"))
                .andExpect(status().isOk());

        verify(unfulfilledShopifyPreviewService).buildPreview(null, 200);
    }

    @Test
    void listUnfulfilled_withAccountAndLimit_capsAtMax() throws Exception {
        UnfulfilledShopifyPreviewResponse body = new UnfulfilledShopifyPreviewResponse();
        when(unfulfilledShopifyPreviewService.buildPreview(BrandAccountKey.STRIKER_STORE, 2000)).thenReturn(body);

        mockMvc.perform(get("/api/orders/unfulfilled-with-tracking-status")
                .param("accountCode", BrandAccountKey.STRIKER_STORE)
                .param("limit", "9999"))
                .andExpect(status().isOk());

        verify(unfulfilledShopifyPreviewService).buildPreview(BrandAccountKey.STRIKER_STORE, 2000);
    }

    @Test
    void fulfillFromTracking_jsonBody_delegates() throws Exception {
        BulkFulfillFromTrackingResponse out = new BulkFulfillFromTrackingResponse();
        when(orderTrackingBulkFulfillmentService.execute(any(BulkFulfillFromTrackingRequest.class))).thenReturn(out);

        mockMvc.perform(post("/api/orders/fulfill-from-tracking")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        verify(orderTrackingBulkFulfillmentService).execute(any(BulkFulfillFromTrackingRequest.class));
    }

    @Test
    void fulfillFromTracking_withBody_delegates() throws Exception {
        BulkFulfillFromTrackingResponse out = new BulkFulfillFromTrackingResponse();
        when(orderTrackingBulkFulfillmentService.execute(any(BulkFulfillFromTrackingRequest.class))).thenReturn(out);

        BulkFulfillFromTrackingRequest req = new BulkFulfillFromTrackingRequest();
        req.setAccountCode(BrandAccountKey.STRIKER_STORE);
        req.setDryRun(true);

        mockMvc.perform(post("/api/orders/fulfill-from-tracking")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(orderTrackingBulkFulfillmentService).execute(any(BulkFulfillFromTrackingRequest.class));
    }
}

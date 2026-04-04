package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.dto.BulkFulfillFromTrackingRequest;
import com.shipway.ordertracking.dto.BulkFulfillFromTrackingResponse;
import com.shipway.ordertracking.dto.FulfillAttemptResult;
import com.shipway.ordertracking.dto.UnfulfilledShopifyOrderItem;
import com.shipway.ordertracking.dto.UnfulfilledShopifyPreviewResponse;
import com.shipway.ordertracking.repository.LabelAwbRepository;
import com.shipway.ordertracking.util.BrandAccountKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTrackingBulkFulfillmentServiceTest {

    @Mock
    private UnfulfilledShopifyPreviewService unfulfilledShopifyPreviewService;

    @Mock
    private ShopifyService shopifyService;

    @Mock
    private LabelAwbRepository labelAwbRepository;

    @Mock
    private com.shipway.ordertracking.config.ShopifyProperties shopifyProperties;

    @InjectMocks
    private OrderTrackingBulkFulfillmentService service;

    @Test
    void mapTrackingStatusToShopifyShipmentStatus_blank_returnsNull() {
        assertNull(OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus(null));
        assertNull(OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("   "));
    }

    @Test
    void mapTrackingStatusToShopifyShipmentStatus_delivered() {
        assertEquals("delivered", OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("Delivered"));
    }

    @Test
    void mapTrackingStatusToShopifyShipmentStatus_inTransit() {
        assertEquals("in_transit",
                OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("In Transit"));
    }

    @Test
    void mapTrackingStatusToShopifyShipmentStatus_outForDelivery() {
        assertEquals("out_for_delivery",
                OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("Out For Delivery"));
    }

    @Test
    void mapTrackingStatusToShopifyShipmentStatus_notDeliveredUndelivered_returnsNull() {
        assertNull(OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("Not Delivered"));
        assertNull(OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("UNDELIVERED"));
    }

    @Test
    void mapTrackingStatusToShopifyShipmentStatus_shippedAndInt() {
        assertEquals("in_transit", OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("SHIPPED"));
        assertEquals("in_transit", OrderTrackingBulkFulfillmentService.mapTrackingStatusToShopifyShipmentStatus("INT"));
    }

    @Test
    void execute_delegatesToPreview() {
        UnfulfilledShopifyPreviewResponse preview = new UnfulfilledShopifyPreviewResponse();
        when(unfulfilledShopifyPreviewService.buildPreview(BrandAccountKey.STRIKER_STORE, 500)).thenReturn(preview);

        BulkFulfillFromTrackingRequest req = new BulkFulfillFromTrackingRequest();
        req.setAccountCode(BrandAccountKey.STRIKER_STORE);

        BulkFulfillFromTrackingResponse out = service.execute(req);
        assertNotNull(out.getSummary());
        assertEquals(0, out.getSummary().get("previewItemsTotal"));
    }

    @Test
    void execute_nullRequest_buildsPreviewWithDefaults() {
        UnfulfilledShopifyPreviewResponse preview = new UnfulfilledShopifyPreviewResponse();
        when(unfulfilledShopifyPreviewService.buildPreview(null, 500)).thenReturn(preview);

        service.execute(null);

        verify(unfulfilledShopifyPreviewService).buildPreview(null, 500);
    }

    @Test
    void execute_capsLimitAtMax() {
        when(unfulfilledShopifyPreviewService.buildPreview(null, 2000)).thenReturn(new UnfulfilledShopifyPreviewResponse());
        BulkFulfillFromTrackingRequest req = new BulkFulfillFromTrackingRequest();
        req.setLimit(9999);
        service.execute(req);
        verify(unfulfilledShopifyPreviewService).buildPreview(null, 2000);
    }

    @Test
    void execute_dryRun_doesNotCallShopifyService() {
        UnfulfilledShopifyOrderItem item = new UnfulfilledShopifyOrderItem();
        item.setAccountCode(BrandAccountKey.STRIKER_STORE);
        item.setOrderId("#1001");
        item.setOrderTrackingStatus("Delivered");
        UnfulfilledShopifyPreviewResponse p = new UnfulfilledShopifyPreviewResponse();
        p.setItems(List.of(item));
        when(unfulfilledShopifyPreviewService.buildPreview(null, 500)).thenReturn(p);

        BulkFulfillFromTrackingRequest req = new BulkFulfillFromTrackingRequest();
        req.setDryRun(true);

        BulkFulfillFromTrackingResponse out = service.execute(req);
        assertTrue(out.isDryRun());
        assertEquals(1, out.getResults().size());
        assertTrue(out.getResults().get(0).isSuccess());
        assertTrue(out.getResults().get(0).getMessage().contains("dryRun"));
        verify(shopifyService, never()).getOrderWithDisplayFulfillmentStatus(anyString(), anyString());
    }

    @Test
    void fulfillPreviewItems_skipsIneligibleStatus() {
        UnfulfilledShopifyOrderItem item = new UnfulfilledShopifyOrderItem();
        item.setAccountCode(BrandAccountKey.STRIKER_STORE);
        item.setOrderId("#1");
        item.setOrderTrackingStatus("MYSTERY_STATUS");

        BulkFulfillFromTrackingResponse out = service.fulfillPreviewItems(List.of(item));
        assertEquals(1, out.getSummary().get("skippedNotDeliveredInTransitOrOfd"));
        assertFalse(out.getResults().get(0).isSuccess());
    }

    @Test
    void fulfillSingleOrder_fulfilledPath_updatesTracking() {
        ShopifyAccount acc = new ShopifyAccount();
        acc.setTrackingUrlTemplate("https://track.example/t/{awb}");
        when(shopifyProperties.getAccountByCode(BrandAccountKey.STRIKER_STORE)).thenReturn(acc);
        when(labelAwbRepository.findLatestAwb(eq(BrandAccountKey.STRIKER_STORE), anyString())).thenReturn(Optional.of("AWB1"));

        Map<String, Object> orderNode = new HashMap<>();
        orderNode.put("id", "gid://shopify/Order/777");
        orderNode.put("displayFulfillmentStatus", "FULFILLED");
        List<Map<String, Object>> fulfillments = new ArrayList<>();
        fulfillments.add(Map.of("id", "gid://shopify/Fulfillment/555"));
        orderNode.put("fulfillments", fulfillments);

        when(shopifyService.getOrderWithDisplayFulfillmentStatus(BrandAccountKey.STRIKER_STORE, "#1001")).thenReturn(orderNode);
        when(shopifyService.updateFulfillmentTracking(eq(BrandAccountKey.STRIKER_STORE), eq(777L), eq(555L), eq("AWB1"), eq("delivered")))
                .thenReturn(true);

        FulfillAttemptResult r = service.fulfillSingleOrder(BrandAccountKey.STRIKER_STORE, "#1001", "Delivered");
        assertTrue(r.isSuccess());
        assertEquals("delivered", r.getShopifyShipmentStatus());
    }

    @Test
    void fulfillSingleOrder_missingOrderInShopify_returnsFailure() {
        when(labelAwbRepository.findLatestAwb(anyString(), anyString())).thenReturn(Optional.of("AWB1"));
        ShopifyAccount acc = new ShopifyAccount();
        acc.setTrackingUrlTemplate("https://track.example/t/{awb}");
        when(shopifyProperties.getAccountByCode(BrandAccountKey.STRIKER_STORE)).thenReturn(acc);
        when(shopifyService.getOrderWithDisplayFulfillmentStatus(BrandAccountKey.STRIKER_STORE, "#1001")).thenReturn(null);

        FulfillAttemptResult r = service.fulfillSingleOrder(BrandAccountKey.STRIKER_STORE, "#1001", "In Transit");
        assertFalse(r.isSuccess());
    }

    @Test
    void fulfillSingleOrder_trackingAccountUppercasedInResult() {
        FulfillAttemptResult r = service.fulfillSingleOrder("striker store", "", "Delivered");
        // Result echoes labels/tracking account_code as uppercase; Shopify map keys are separate (e.g. strikerstore).
        assertEquals("STRIKER STORE", r.getAccountCode());
    }

    @Test
    void fulfillSingleOrder_missingAccount_returnsError() {
        FulfillAttemptResult r = service.fulfillSingleOrder(null, "#1", "Delivered");
        assertFalse(r.isSuccess());
        assertEquals("accountCode and orderId are required", r.getMessage());
    }

    @Test
    void fulfillSingleOrder_ineligibleStatus_returnsError() {
        FulfillAttemptResult r = service.fulfillSingleOrder(BrandAccountKey.STRIKER_STORE, "#1", "MYSTERY");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("does not map"));
    }
}

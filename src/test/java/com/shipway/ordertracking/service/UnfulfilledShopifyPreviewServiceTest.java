package com.shipway.ordertracking.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnfulfilledShopifyPreviewServiceTest {

    @Test
    void normalizeShipmentStatus_collapsesUnderscoresAndSpaces() {
        assertEquals("IN TRANSIT", UnfulfilledShopifyPreviewService.normalizeShipmentStatus("  in_transit  "));
        assertEquals("OUT FOR DELIVERY", UnfulfilledShopifyPreviewService.normalizeShipmentStatus("OUT_FOR_DELIVERY"));
    }

    @Test
    void normalizeShipmentStatus_null_returnsEmpty() {
        assertEquals("", UnfulfilledShopifyPreviewService.normalizeShipmentStatus(null));
    }

    @Test
    void matchesUserRequestedStatuses_empty_returnsFalse() {
        assertFalse(UnfulfilledShopifyPreviewService.matchesUserRequestedStatuses(null));
        assertFalse(UnfulfilledShopifyPreviewService.matchesUserRequestedStatuses("   "));
    }

    @Test
    void matchesUserRequestedStatuses_knownCarrierStatus() {
        assertTrue(UnfulfilledShopifyPreviewService.matchesUserRequestedStatuses("In Transit"));
        assertTrue(UnfulfilledShopifyPreviewService.matchesUserRequestedStatuses("SHIPPED"));
    }

    @Test
    void matchesUserRequestedStatuses_unknown_returnsFalse() {
        assertFalse(UnfulfilledShopifyPreviewService.matchesUserRequestedStatuses("Totally Unknown Carrier Status XYZ"));
    }

    @Test
    void normalizeShipmentStatus_whitespaceOnly_returnsEmpty() {
        assertEquals("", UnfulfilledShopifyPreviewService.normalizeShipmentStatus("   "));
    }

    @Test
    void normalizeShipmentStatus_pickedUpVariant() {
        assertEquals("PICKED UP", UnfulfilledShopifyPreviewService.normalizeShipmentStatus("picked_up"));
    }
}

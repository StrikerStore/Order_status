package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.config.ShopifyProperties;
import com.shipway.ordertracking.dto.UnfulfilledShopifyPreviewResponse;
import com.shipway.ordertracking.entity.StoreShopifyConnection;
import com.shipway.ordertracking.repository.OrderTrackingRepository;
import com.shipway.ordertracking.repository.StoreShopifyConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UnfulfilledShopifyPreviewService#buildPreview} filter vs {@code store_shopify_connections.brand_name}
 * (normalized match: {@code strikerstore} ↔ {@code STRIKER STORE}).
 */
@ExtendWith(MockitoExtension.class)
class UnfulfilledShopifyPreviewServiceFilterTest {

    @Mock
    private OrderTrackingRepository orderTrackingRepository;

    @Mock
    private ShopifyService shopifyService;

    @Mock
    private ShopifyProperties shopifyProperties;

    @Mock
    private StoreShopifyConnectionRepository storeShopifyConnectionRepository;

    @InjectMocks
    private UnfulfilledShopifyPreviewService service;

    @BeforeEach
    void injectBulkQueryProps() {
        ReflectionTestUtils.setField(service, "bulkOrdersQuery", "fulfillment_status:unfulfilled");
        ReflectionTestUtils.setField(service, "bulkOrdersMaxDefault", 5000);
    }

    @Test
    void buildPreview_filterStrikerstore_resolvesRowWithStrikerStoreSpacedBrand() {
        StoreShopifyConnection c = new StoreShopifyConnection();
        c.setBrandName("STRIKER STORE");
        c.setAccountCode("PLX001");

        when(storeShopifyConnectionRepository.count()).thenReturn(1L);
        when(storeShopifyConnectionRepository.findAllByOrderByBrandNameAsc()).thenReturn(List.of(c));
        when(shopifyProperties.getAccountByCode("STRIKER STORE")).thenReturn(new ShopifyAccount());
        when(shopifyService.loadOrderNodesBySearchQueryPaged(anyString(), anyString(), anyInt())).thenReturn(Collections.emptyMap());

        UnfulfilledShopifyPreviewResponse r = service.buildPreview("strikerstore", 200);

        verify(shopifyService).loadOrderNodesBySearchQueryPaged(eq("STRIKER STORE"), eq("fulfillment_status:unfulfilled"), eq(200));
        assertEquals(1, r.getCounts().get("accountsProcessed"));
    }

    @Test
    void buildPreview_filterMatchesByTrackingAccountCodeNormalized() {
        StoreShopifyConnection c = new StoreShopifyConnection();
        c.setBrandName("STRIKER STORE");
        c.setAccountCode("PLX 001");

        when(storeShopifyConnectionRepository.count()).thenReturn(1L);
        when(storeShopifyConnectionRepository.findAllByOrderByBrandNameAsc()).thenReturn(List.of(c));
        when(shopifyProperties.getAccountByCode("STRIKER STORE")).thenReturn(new ShopifyAccount());
        when(shopifyService.loadOrderNodesBySearchQueryPaged(anyString(), anyString(), anyInt())).thenReturn(Map.of());

        UnfulfilledShopifyPreviewResponse r = service.buildPreview("plx001", 200);

        verify(shopifyService).loadOrderNodesBySearchQueryPaged(eq("STRIKER STORE"), anyString(), anyInt());
        assertEquals(1, r.getCounts().get("accountsProcessed"));
    }
}

package com.shipway.ordertracking.service;

import com.shipway.ordertracking.entity.StoreShopifyConnection;
import com.shipway.ordertracking.repository.StoreShopifyConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreShopifyBrandAccountServiceTest {

    @Mock
    private StoreShopifyConnectionRepository repository;

    @InjectMocks
    private StoreShopifyBrandAccountService service;

    @Test
    void normalizeBrandKey_stripsSpacesAndLowercases() {
        assertEquals("strikerstore", StoreShopifyBrandAccountService.normalizeBrandKey("STRIKER STORE"));
        assertEquals("strikerstore", StoreShopifyBrandAccountService.normalizeBrandKey("strikerstore"));
        assertEquals("dribblestore", StoreShopifyBrandAccountService.normalizeBrandKey("DRIBBLE STORE"));
    }

    @Test
    void findTrackingAccountCode_appKeyMatchesLegacySpacedBrandInDb() {
        StoreShopifyConnection row = new StoreShopifyConnection();
        row.setBrandName("STRIKER STORE");
        row.setAccountCode("PLX_STRIKER");
        when(repository.findAllByOrderByBrandNameAsc()).thenReturn(List.of(row));

        Optional<String> acct = service.findTrackingAccountCode("strikerstore");
        assertTrue(acct.isPresent());
        assertEquals("PLX_STRIKER", acct.get());
    }

    @Test
    void findTrackingAccountCode_lookupSpacedMatchesCompactDbBrand() {
        StoreShopifyConnection row = new StoreShopifyConnection();
        row.setBrandName("strikerstore");
        row.setAccountCode("X1");
        when(repository.findAllByOrderByBrandNameAsc()).thenReturn(List.of(row));

        assertEquals("X1", service.findTrackingAccountCode("STRIKER STORE").orElseThrow());
    }
}

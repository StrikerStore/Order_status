package com.shipway.ordertracking.config;

import com.shipway.ordertracking.util.BrandAccountKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShopifyPropertiesAccountLookupTest {

    @Test
    void getAccountByCode_matchesConfiguredKey() {
        ShopifyProperties p = new ShopifyProperties();
        ShopifyAccount acc = new ShopifyAccount();
        p.setAccounts(Map.of(BrandAccountKey.STRIKER_STORE, acc));
        assertNotNull(p.getAccountByCode(BrandAccountKey.STRIKER_STORE));
        assertNotNull(p.getAccountByCode("striker store"));
    }

    @Test
    void getAccountByCode_unknownReturnsNull() {
        ShopifyProperties p = new ShopifyProperties();
        p.setAccounts(Map.of(BrandAccountKey.STRIKER_STORE, new ShopifyAccount()));
        assertNull(p.getAccountByCode("OTHER"));
    }
}

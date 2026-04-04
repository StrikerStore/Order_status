package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.dto.FasterrAbandonedCartWebhook;
import com.shipway.ordertracking.util.BrandAccountKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbandonedCartFlowServiceTest {

    @Mock
    private BotspaceService botspaceService;

    @Mock
    private BotspaceProperties botspaceProperties;

    @Mock
    private ShopifyService shopifyService;

    @InjectMocks
    private AbandonedCartFlowService service;

    @BeforeEach
    void clearTestPhone() {
        ReflectionTestUtils.setField(service, "abandonedCartTestPhone", "");
    }

    @Test
    void processAbandonedCart_nullWebhook_returnsFalse() {
        assertFalse(service.processAbandonedCart(null));
    }

    @Test
    void processAbandonedCart_nullAttributes_returnsFalse() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        assertFalse(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_missingPhone_returnsFalse() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        w.setAttributes(attrs);
        assertFalse(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_schedulesNotification_returnsTrue() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        attrs.setPhoneNumber("+919876543210");
        FasterrAbandonedCartWebhook.CustomAttributes ca = new FasterrAbandonedCartWebhook.CustomAttributes();
        ca.setLandingPageUrl("https://www.thestrikerstore.com/cart");
        attrs.setCustomAttributes(ca);
        FasterrAbandonedCartWebhook.CartItem item = new FasterrAbandonedCartWebhook.CartItem();
        item.setProductId(123L);
        item.setImgUrl("https://cdn.example.com/i.jpg");
        attrs.setItems(List.of(item));
        w.setAttributes(attrs);

        when(shopifyService.getProductOnlineStoreUrl(BrandAccountKey.STRIKER_STORE, 123L)).thenReturn("https://shop.example/p/1");

        assertTrue(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_phoneFromShippingAddress() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        FasterrAbandonedCartWebhook.Address ship = new FasterrAbandonedCartWebhook.Address();
        ship.setPhone("+919876543210");
        attrs.setShippingAddress(ship);
        FasterrAbandonedCartWebhook.CustomAttributes ca = new FasterrAbandonedCartWebhook.CustomAttributes();
        ca.setLandingPageUrl("https://www.thestrikerstore.com/cart");
        attrs.setCustomAttributes(ca);
        w.setAttributes(attrs);

        assertTrue(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_phoneFromBillingAddress() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        FasterrAbandonedCartWebhook.Address bill = new FasterrAbandonedCartWebhook.Address();
        bill.setPhone("+919876543210");
        attrs.setBillingAddress(bill);
        FasterrAbandonedCartWebhook.CustomAttributes ca = new FasterrAbandonedCartWebhook.CustomAttributes();
        ca.setLandingPageUrl("https://www.thestrikerstore.com/cart");
        attrs.setCustomAttributes(ca);
        w.setAttributes(attrs);

        assertTrue(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_extractsDribbleStoreFromHost() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        attrs.setPhoneNumber("+919876543210");
        FasterrAbandonedCartWebhook.CustomAttributes ca = new FasterrAbandonedCartWebhook.CustomAttributes();
        ca.setLandingPageUrl("https://www.thedribblestore.com/cart");
        attrs.setCustomAttributes(ca);
        FasterrAbandonedCartWebhook.CartItem item = new FasterrAbandonedCartWebhook.CartItem();
        item.setProductId(1L);
        attrs.setItems(List.of(item));
        w.setAttributes(attrs);

        when(shopifyService.getProductOnlineStoreUrl(BrandAccountKey.DRIBBLE_STORE, 1L)).thenReturn("https://recovery.example/p");

        assertTrue(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_usesCheckoutUrlWhenLandingMissing() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        attrs.setPhoneNumber("+919876543210");
        attrs.setCheckoutUrl("https://www.thestrikerstore.com/checkout");
        FasterrAbandonedCartWebhook.CartItem item = new FasterrAbandonedCartWebhook.CartItem();
        item.setProductId(99L);
        attrs.setItems(List.of(item));
        w.setAttributes(attrs);

        when(shopifyService.getProductOnlineStoreUrl(BrandAccountKey.STRIKER_STORE, 99L)).thenReturn("https://p.example/99");

        assertTrue(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_testPhoneOverride() {
        ReflectionTestUtils.setField(service, "abandonedCartTestPhone", "+919990000000");

        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        attrs.setPhoneNumber("+919876543210");
        FasterrAbandonedCartWebhook.CustomAttributes ca = new FasterrAbandonedCartWebhook.CustomAttributes();
        ca.setLandingPageUrl("https://www.thestrikerstore.com/cart");
        attrs.setCustomAttributes(ca);
        FasterrAbandonedCartWebhook.CartItem item = new FasterrAbandonedCartWebhook.CartItem();
        item.setProductId(1L);
        attrs.setItems(List.of(item));
        w.setAttributes(attrs);

        when(shopifyService.getProductOnlineStoreUrl(BrandAccountKey.STRIKER_STORE, 1L)).thenReturn("https://shop.example/p/1");

        assertTrue(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_defaultBrandWhenNoMarketingUrl() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        attrs.setPhoneNumber("+919876543210");
        w.setAttributes(attrs);

        assertTrue(service.processAbandonedCart(w));
    }

    @Test
    void processAbandonedCart_unknownDomainUppercasesAsBrand() {
        FasterrAbandonedCartWebhook w = new FasterrAbandonedCartWebhook();
        FasterrAbandonedCartWebhook.Attributes attrs = new FasterrAbandonedCartWebhook.Attributes();
        attrs.setCartId("cart-1");
        attrs.setPhoneNumber("+919876543210");
        FasterrAbandonedCartWebhook.CustomAttributes ca = new FasterrAbandonedCartWebhook.CustomAttributes();
        ca.setLandingPageUrl("https://www.otherbrand.com/cart");
        attrs.setCustomAttributes(ca);
        FasterrAbandonedCartWebhook.CartItem item = new FasterrAbandonedCartWebhook.CartItem();
        item.setProductId(5L);
        attrs.setItems(List.of(item));
        w.setAttributes(attrs);

        when(shopifyService.getProductOnlineStoreUrl("OTHERBRAND", 5L)).thenReturn("");

        assertTrue(service.processAbandonedCart(w));
    }
}

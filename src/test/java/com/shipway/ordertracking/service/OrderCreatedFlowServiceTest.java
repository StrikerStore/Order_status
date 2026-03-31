package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.config.ShopifyAccount;
import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.dto.ShopifyOrderCreatedWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreatedFlowServiceTest {

    @Mock
    private BotspaceService botspaceService;

    @Mock
    private ShopifyService shopifyService;

    @Mock
    private com.shipway.ordertracking.config.ShopifyProperties shopifyProperties;

    @Mock
    private BotspaceProperties botspaceProperties;

    @InjectMocks
    private OrderCreatedFlowService service;

    @BeforeEach
    void clearTestPhoneOverride() {
        ReflectionTestUtils.setField(service, "orderCreatedTestPhone", "");
    }

    @Test
    void processShopifyOrderCreated_nullShopDomain_returnsFalse() {
        ShopifyOrderCreatedWebhook w = new ShopifyOrderCreatedWebhook();
        w.setName("#1001");
        w.setPhone("+919876543210");
        assertFalse(service.processShopifyOrderCreated(w, null));
    }

    @Test
    void processShopifyOrderCreated_emptyOrderName_returnsFalse() {
        ShopifyOrderCreatedWebhook w = new ShopifyOrderCreatedWebhook();
        w.setName("");
        w.setPhone("+919876543210");
        assertFalse(service.processShopifyOrderCreated(w, "shop.myshopify.com"));
    }

    @Test
    void processShopifyOrderCreated_missingPhone_returnsFalse() {
        ShopifyOrderCreatedWebhook w = new ShopifyOrderCreatedWebhook();
        w.setName("#1001");
        assertFalse(service.processShopifyOrderCreated(w, "seq5t1-mz.myshopify.com"));
    }

    @Test
    void processShopifyOrderCreated_success_sendsBotspace() {
        ShopifyAccount sa = new ShopifyAccount();
        sa.setShop("seq5t1-mz.myshopify.com");
        Map<String, ShopifyAccount> accounts = new HashMap<>();
        accounts.put("STRI", sa);
        when(shopifyProperties.getAccounts()).thenReturn(accounts);
        when(shopifyProperties.getAccountByCode("STRI")).thenReturn(sa);

        BotspaceAccount ba = new BotspaceAccount();
        ba.setOrderCreatedTemplateId("tpl_order_created");
        when(botspaceProperties.getAccountByCode("STRI")).thenReturn(ba);

        when(botspaceService.sendTemplateMessage(eq("STRI"), any(BotspaceMessageRequest.class), eq("#1001"),
                eq("sent_orderCreated"), eq("failed_orderCreated"))).thenReturn(true);

        ShopifyOrderCreatedWebhook w = new ShopifyOrderCreatedWebhook();
        w.setName("#1001");
        w.setPhone("+919876543210");

        assertTrue(service.processShopifyOrderCreated(w, "seq5t1-mz.myshopify.com"));

        ArgumentCaptor<BotspaceMessageRequest> cap = ArgumentCaptor.forClass(BotspaceMessageRequest.class);
        verify(botspaceService).sendTemplateMessage(eq("STRI"), cap.capture(), eq("#1001"), eq("sent_orderCreated"),
                eq("failed_orderCreated"));
        assertEquals("tpl_order_created", cap.getValue().getTemplateId());
    }

    @Test
    void processShopifyOrderCreated_templateMissing_returnsFalse() {
        ShopifyAccount sa = new ShopifyAccount();
        sa.setShop("seq5t1-mz.myshopify.com");
        when(shopifyProperties.getAccounts()).thenReturn(Map.of("STRI", sa));
        when(shopifyProperties.getAccountByCode("STRI")).thenReturn(sa);
        when(botspaceProperties.getAccountByCode("STRI")).thenReturn(new BotspaceAccount());

        ShopifyOrderCreatedWebhook w = new ShopifyOrderCreatedWebhook();
        w.setName("#1001");
        w.setPhone("+919876543210");

        assertFalse(service.processShopifyOrderCreated(w, "seq5t1-mz.myshopify.com"));
    }
}

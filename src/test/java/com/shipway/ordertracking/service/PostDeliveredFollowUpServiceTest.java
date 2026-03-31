package com.shipway.ordertracking.service;

import com.shipway.ordertracking.config.BotspaceAccount;
import com.shipway.ordertracking.config.BotspaceProperties;
import com.shipway.ordertracking.dto.BotspaceMessageRequest;
import com.shipway.ordertracking.repository.OrderPhoneProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDeliveredFollowUpServiceTest {

    @Mock
    private CustomerMessageTrackingService customerMessageTrackingService;

    @Mock
    private BotspaceService botspaceService;

    @Mock
    private BotspaceProperties botspaceProperties;

    @InjectMocks
    private PostDeliveredFollowUpService service;

    @BeforeEach
    void initProps() {
        ReflectionTestUtils.setField(service, "communityUrl", "https://chat.whatsapp.com/test");
        ReflectionTestUtils.setField(service, "postDeliveredFollowUpTestPhone", "");
    }

    @Test
    void sendFollowUpToYesterdayDelivered_noRows_doesNotSend() {
        when(customerMessageTrackingService.findOrderIdAndPhoneForSentDeliveredYesterday()).thenReturn(Collections.emptyList());

        service.sendFollowUpToYesterdayDelivered();

        verify(botspaceService, never()).sendTemplateMessage(anyString(), any(BotspaceMessageRequest.class), anyString(),
                anyString(), anyString());
    }

    @Test
    void sendFollowUpToYesterdayDelivered_sendsWhenEligible() {
        OrderPhoneProjection row = mock(OrderPhoneProjection.class);
        when(row.getOrderId()).thenReturn("#1001");
        when(row.getAccountCode()).thenReturn("STRI");
        when(row.getShippingPhone()).thenReturn("+919876543210");

        when(customerMessageTrackingService.findOrderIdAndPhoneForSentDeliveredYesterday()).thenReturn(List.of(row));
        when(customerMessageTrackingService.hasAnyStatus(eq("#1001"), eq("STRI"), anyList())).thenReturn(false);

        BotspaceAccount ba = new BotspaceAccount();
        ba.setPostDeliveredFollowUpTemplateId("tpl_followup");
        when(botspaceProperties.getAccountByCode("STRI")).thenReturn(ba);

        when(botspaceService.sendTemplateMessage(eq("STRI"), any(BotspaceMessageRequest.class), eq("#1001"),
                eq("sent_postDeliveredFollowUp"), eq("failed_postDeliveredFollowUp"))).thenReturn(true);

        service.sendFollowUpToYesterdayDelivered();

        verify(botspaceService).sendTemplateMessage(eq("STRI"), any(BotspaceMessageRequest.class), eq("#1001"),
                eq("sent_postDeliveredFollowUp"), eq("failed_postDeliveredFollowUp"));
    }

    @Test
    void sendFollowUpToYesterdayDelivered_skipsWhenAlreadyFollowedUp() {
        OrderPhoneProjection row = mock(OrderPhoneProjection.class);
        when(row.getOrderId()).thenReturn("#1001");
        when(row.getAccountCode()).thenReturn("STRI");

        when(customerMessageTrackingService.findOrderIdAndPhoneForSentDeliveredYesterday()).thenReturn(List.of(row));
        when(customerMessageTrackingService.hasAnyStatus(eq("#1001"), eq("STRI"), anyList())).thenReturn(true);

        service.sendFollowUpToYesterdayDelivered();

        verify(botspaceService, never()).sendTemplateMessage(anyString(), any(BotspaceMessageRequest.class), anyString(),
                anyString(), anyString());
    }
}

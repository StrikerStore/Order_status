package com.shipway.ordertracking.service;

import com.shipway.ordertracking.entity.CustomerMessageTracking;
import com.shipway.ordertracking.repository.CustomerMessageTrackingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerMessageTrackingServiceTest {

    @Mock
    private CustomerMessageTrackingRepository repository;

    @InjectMocks
    private CustomerMessageTrackingService service;

    @Test
    void hasAnyStatus_nullOrderId_returnsFalse() {
        assertFalse(service.hasAnyStatus(null, "STRIKER STORE", List.of("sent")));
    }

    @Test
    void hasAnyStatus_emptyStatuses_returnsFalse() {
        assertFalse(service.hasAnyStatus("1", "X", List.of()));
    }

    @Test
    void hasAnyStatus_delegatesToRepository() {
        when(repository.countByOrderBrandAndMessageStatusIn("1", "BR", List.of("a", "b"))).thenReturn(1L);
        assertTrue(service.hasAnyStatus("1", "BR", List.of("a", "b")));
    }

    @Test
    void hasStatus_null_returnsFalse() {
        assertFalse(service.hasStatus("1", "B", null));
    }

    @Test
    void hasStatus_delegates() {
        when(repository.countByOrderBrandAndMessageStatus("1", "BR", "sent")).thenReturn(1L);
        assertTrue(service.hasStatus("1", "BR", "sent"));
    }

    @Test
    void addStatus_emptyOrderId_returnsFalse() {
        assertFalse(service.addStatus("", "PLX001", "sent"));
        verify(repository, never()).save(any());
    }

    @Test
    void addStatus_bothAccountAndBrandEmpty_returnsFalse() {
        assertFalse(service.addStatus("1", "", "sent", ""));
        verify(repository, never()).save(any());
    }

    @Test
    void addStatus_alreadyExists_skipsSave() {
        when(repository.countByOrderBrandAndMessageStatus("1", "", "sent")).thenReturn(1L);
        assertTrue(service.addStatus("1", "PLX001", "sent"));
        verify(repository, never()).save(any());
    }

    @Test
    void addStatus_insertsNewRecord() {
        when(repository.countByOrderBrandAndMessageStatus("1", "", "sent")).thenReturn(0L);
        assertTrue(service.addStatus("1", "PLX001", "sent"));
        verify(repository).save(any(CustomerMessageTracking.class));
    }

    @Test
    void addStatus_withBrand_insertsWithBrandDedup() {
        when(repository.countByOrderBrandAndMessageStatus(eq("1"), eq("Plex"), eq("sent"))).thenReturn(0L);
        assertTrue(service.addStatus("1", "PLX", "sent", "Plex"));
        verify(repository).save(any(CustomerMessageTracking.class));
    }
}

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
        assertFalse(service.hasAnyStatus(null, "STRI", List.of("sent")));
    }

    @Test
    void hasAnyStatus_emptyStatuses_returnsFalse() {
        assertFalse(service.hasAnyStatus("1", "STRI", List.of()));
    }

    @Test
    void hasAnyStatus_delegatesToRepository() {
        when(repository.existsByOrderIdAndAccountCodeAndMessageStatusIn("1", "STRI", List.of("a", "b"))).thenReturn(true);
        assertTrue(service.hasAnyStatus("1", "STRI", List.of("a", "b")));
    }

    @Test
    void hasStatus_null_returnsFalse() {
        assertFalse(service.hasStatus("1", "STRI", null));
    }

    @Test
    void hasStatus_delegates() {
        when(repository.existsByOrderIdAndAccountCodeAndMessageStatus("1", "STRI", "sent")).thenReturn(true);
        assertTrue(service.hasStatus("1", "STRI", "sent"));
    }

    @Test
    void addStatus_emptyOrderId_returnsFalse() {
        assertFalse(service.addStatus("", "STRI", "sent"));
        verify(repository, never()).save(any());
    }

    @Test
    void addStatus_alreadyExists_skipsSave() {
        when(repository.existsByOrderIdAndAccountCodeAndMessageStatus("1", "STRI", "sent")).thenReturn(true);
        assertTrue(service.addStatus("1", "STRI", "sent"));
        verify(repository, never()).save(any());
    }

    @Test
    void addStatus_insertsNewRecord() {
        when(repository.existsByOrderIdAndAccountCodeAndMessageStatus("1", "STRI", "sent")).thenReturn(false);
        assertTrue(service.addStatus("1", "STRI", "sent"));
        verify(repository).save(any(CustomerMessageTracking.class));
    }
}

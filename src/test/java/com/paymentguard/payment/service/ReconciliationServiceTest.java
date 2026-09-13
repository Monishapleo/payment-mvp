package com.paymentguard.payment.service;

import com.paymentguard.payment.entity.Payment;
import com.paymentguard.payment.entity.PaymentStatus;
import com.paymentguard.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentProvider paymentProvider;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @Test
    void shouldReconcileInProgressPaymentWhenProviderSucceeds() {

        // Arrange
        Payment payment = new Payment();

        payment.setOrderId(10L);
        payment.setStatus(PaymentStatus.IN_PROGRESS);
        payment.setProviderReference("PROVIDER-123");

        when(paymentRepository.findAll())
                .thenReturn(List.of(payment));

        when(paymentProvider.getStatus(payment.getId(),"PROVIDER-123"))
                .thenReturn(
                        new PaymentProvider.ProviderResult(
                                "PROVIDER-123",
                                true
                        )
                );

        // Act
        reconciliationService.reconcile();

        // Assert
        verify(paymentProvider)
                .getStatus(payment.getId(),"PROVIDER-123");

        verify(paymentService)
                .completePayment(
                        eq(payment.getId()),
                        eq(true),
                        eq("PROVIDER-123")
                );
    }

    @Test
    void shouldSkipPaymentWithoutProviderReference() {

        // Arrange
        Payment payment = new Payment();

        payment.setOrderId(10L);
        payment.setStatus(PaymentStatus.IN_PROGRESS);
        payment.setProviderReference(null);

        when(paymentRepository.findAll())
                .thenReturn(List.of(payment));

        // Act
        reconciliationService.reconcile();

        // Assert
        verify(paymentProvider, never())
                .getStatus(anyLong(),anyString());

        verify(paymentService, never())
                .completePayment(
                        any(),
                        anyBoolean(),
                        any()
                );
    }
}
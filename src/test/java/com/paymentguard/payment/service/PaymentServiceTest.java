package com.paymentguard.payment.service;

import com.paymentguard.payment.dto.CreatePaymentRequest;
import com.paymentguard.payment.entity.Payment;
import com.paymentguard.payment.entity.PaymentMethod;
import com.paymentguard.payment.entity.PaymentStatus;
import com.paymentguard.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @InjectMocks
    private PaymentService paymentService;


    @Test
    void shouldReturnExistingPaymentForSameIdempotencyKey() {

        // Arrange
        Long userId = 1L;
        String idempotencyKey = "payment-key-123";

        Payment existingPayment = new Payment();
        existingPayment.setOrderId(10L);
        existingPayment.setAmount(new BigDecimal("500.00"));
        existingPayment.setIdempotencyKey(idempotencyKey);
        existingPayment.setPaymentMethod(PaymentMethod.UPI);
        existingPayment.setStatus(PaymentStatus.SUCCESS);

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        10L,
                        PaymentMethod.UPI
                );

        when(paymentTransactionService.claimPayment(
                anyLong(),
                any(CreatePaymentRequest.class),
                anyString()
        )).thenReturn(
                new PaymentTransactionService.ClaimResult(
                        existingPayment,
                        false
                )
        );

        // Act
        var response = paymentService.create(
                userId,
                request,
                idempotencyKey
        );

        // Assert
        assertEquals(
                PaymentStatus.SUCCESS,
                response.status()
        );

        assertEquals(
                new BigDecimal("500.00"),
                response.amount()
        );

        verify(paymentProvider, never())
                .charge(anyLong(), any());

        verify(paymentTransactionService)
                .claimPayment(
                        userId,
                        request,
                        idempotencyKey
                );
    }


    @Test
    void shouldMarkPaymentAndOrderAsConfirmedWhenProviderSucceeds() {

        // Arrange
        Long userId = 1L;
        Long orderId = 10L;
        String idempotencyKey = "payment-success-key";

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        orderId,
                        PaymentMethod.UPI
                );

        when(paymentTransactionService.claimPayment(
                anyLong(),
                any(CreatePaymentRequest.class),
                anyString()
        )).thenReturn(
                new PaymentTransactionService.ClaimResult(
                        payment,
                        true
                )
        );

        when(paymentRepository.findById(payment.getId()))
                .thenReturn(Optional.of(payment));

        when(paymentProvider.charge(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(
                new PaymentProvider.ProviderResult(
                        "PROVIDER-123",
                        true
                )
        );

        // Act
        paymentService.create(
                userId,
                request,
                idempotencyKey
        );

        // Assert
        verify(paymentProvider)
                .charge(
                        payment.getId(),
                        payment.getAmount()
                );

        verify(paymentTransactionService)
                .completePayment(
                        payment.getId(),
                        true,
                        "PROVIDER-123"
                );
    }


    @Test
    void shouldMarkPaymentAndOrderAsFailedWhenProviderFails() {

        // Arrange
        Long userId = 1L;
        Long orderId = 10L;
        String idempotencyKey = "payment-failure-key";

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        orderId,
                        PaymentMethod.UPI
                );

        when(paymentTransactionService.claimPayment(
                anyLong(),
                any(CreatePaymentRequest.class),
                anyString()
        )).thenReturn(
                new PaymentTransactionService.ClaimResult(
                        payment,
                        true
                )
        );

        when(paymentRepository.findById(payment.getId()))
                .thenReturn(Optional.of(payment));

        when(paymentProvider.charge(
                payment.getId(),
                payment.getAmount()
        )).thenReturn(
                new PaymentProvider.ProviderResult(
                        "PROVIDER-FAIL-123",
                        false
                )
        );

        // Act
        paymentService.create(
                userId,
                request,
                idempotencyKey
        );

        // Assert
        verify(paymentProvider)
                .charge(
                        payment.getId(),
                        payment.getAmount()
                );

        verify(paymentTransactionService)
                .completePayment(
                        payment.getId(),
                        false,
                        "PROVIDER-FAIL-123"
                );
    }
}
package com.paymentguard.payment.service;

import com.paymentguard.order.entity.Order;
import com.paymentguard.order.entity.OrderStatus;
import com.paymentguard.order.repository.OrderRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentProvider paymentProvider;

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

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingPayment));

        Order order = new Order();
        order.setUserId(userId);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findById(existingPayment.getOrderId()))
                .thenReturn(Optional.of(order));

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        10L,
                        PaymentMethod.UPI
                );

        // Act
        var response = paymentService.create(
                userId,
                request,
                idempotencyKey
        );

        // Assert
        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(new BigDecimal("500.00"), response.amount());

        // Very important:
        // Provider must NOT be called again.
        verify(paymentProvider, never())
                .charge(anyLong(), any());

        // Payment must not be inserted again.
        verify(paymentRepository, never())
                .save(any(Payment.class));
    }
    @Test
    void shouldRejectPaymentWhenOrderIsAlreadyPaid() {

        // Arrange
        Long userId = 1L;
        Long orderId = 10L;

        Order order = new Order();
        order.setUserId(userId);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        orderId,
                        PaymentMethod.UPI
                );

        // Act + Assert
        var exception = assertThrows(
                RuntimeException.class,
                () -> paymentService.create(
                        userId,
                        request,
                        "different-key"
                )
        );

        assertTrue(
                exception.getMessage().contains("Order is already paid")
        );

        // Provider must never be called.
        verify(paymentProvider, never())
                .charge(anyLong(), any());

        // No new payment should be created.
        verify(paymentRepository, never())
                .save(any(Payment.class));
    }
    @Test
    void shouldMarkPaymentAndOrderAsConfirmedWhenProviderSucceeds() {

        // Arrange
        Long userId = 1L;
        Long orderId = 10L;
        String idempotencyKey = "payment-success-key";

        Order order = new Order();
        order.setUserId(userId);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CREATED);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(payment);

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

        // completePayment() will fetch the payment again
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        // Act
        paymentService.create(
                userId,
                new CreatePaymentRequest(
                        orderId,
                        PaymentMethod.UPI
                ),
                idempotencyKey
        );

        // Assert
        assertEquals(
                PaymentStatus.SUCCESS,
                payment.getStatus()
        );

        assertEquals(
                OrderStatus.CONFIRMED,
                order.getStatus()
        );

        assertEquals(
                "PROVIDER-123",
                payment.getProviderReference()
        );

        verify(paymentProvider)
                .charge(payment.getId(), payment.getAmount());
    }
    @Test
    void shouldMarkPaymentAndOrderAsFailedWhenProviderFails() {

        // Arrange
        Long userId = 1L;
        Long orderId = 10L;
        String idempotencyKey = "payment-failure-key";

        Order order = new Order();
        order.setUserId(userId);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CREATED);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(payment);

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
                new CreatePaymentRequest(
                        orderId,
                        PaymentMethod.UPI
                ),
                idempotencyKey
        );

        // Assert
        assertEquals(
                PaymentStatus.FAILURE,
                payment.getStatus()
        );

        assertEquals(
                OrderStatus.PAYMENT_FAILED,
                order.getStatus()
        );

        assertEquals(
                "PROVIDER-FAIL-123",
                payment.getProviderReference()
        );

        verify(paymentProvider)
                .charge(payment.getId(), payment.getAmount());
    }
}
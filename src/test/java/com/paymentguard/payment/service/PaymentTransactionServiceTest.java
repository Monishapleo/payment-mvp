package com.paymentguard.payment.service;

import com.paymentguard.common.exception.ApiException;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentTransactionService paymentTransactionService;


    @Test
    void shouldRejectPaymentWhenOrderIsAlreadyPaid() {

        // Arrange
        Long userId = 1L;
        Long orderId = 10L;

        Order order = new Order();
        order.setUserId(userId);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CONFIRMED);

        when(paymentRepository.findByIdempotencyKey("different-key"))
                .thenReturn(Optional.empty());

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        orderId,
                        PaymentMethod.UPI
                );

        // Act + Assert
        ApiException exception = assertThrows(
                ApiException.class,
                () -> paymentTransactionService.claimPayment(
                        userId,
                        request,
                        "different-key"
                )
        );

        assertEquals(
                "Order is already paid",
                exception.getMessage()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }


    @Test
    void shouldCreatePaymentInProgress() {

        // Arrange
        Long userId = 1L;
        Long orderId = 10L;
        String idempotencyKey = "new-payment-key";

        Order order = new Order();
        order.setUserId(userId);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CREATED);

        Payment savedPayment = new Payment();
        savedPayment.setOrderId(orderId);
        savedPayment.setAmount(new BigDecimal("500.00"));
        savedPayment.setIdempotencyKey(idempotencyKey);
        savedPayment.setPaymentMethod(PaymentMethod.UPI);
        savedPayment.setStatus(PaymentStatus.IN_PROGRESS);

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(savedPayment);

        // Act
        PaymentTransactionService.ClaimResult result =
                paymentTransactionService.claimPayment(
                        userId,
                        new CreatePaymentRequest(
                                orderId,
                                PaymentMethod.UPI
                        ),
                        idempotencyKey
                );

        // Assert
        assertTrue(result.newlyCreated());
        assertEquals(
                PaymentStatus.IN_PROGRESS,
                result.payment().getStatus()
        );

        assertEquals(
                OrderStatus.PAYMENT_PENDING,
                order.getStatus()
        );

        verify(paymentRepository)
                .save(any(Payment.class));

        verify(orderRepository)
                .save(order);
    }


    @Test
    void shouldReturnExistingPaymentForSameIdempotencyKey() {

        // Arrange
        Long userId = 1L;
        String idempotencyKey = "existing-key";

        Payment existingPayment = new Payment();
        existingPayment.setOrderId(10L);
        existingPayment.setAmount(new BigDecimal("500.00"));
        existingPayment.setIdempotencyKey(idempotencyKey);
        existingPayment.setPaymentMethod(PaymentMethod.UPI);
        existingPayment.setStatus(PaymentStatus.IN_PROGRESS);

        Order order = new Order();
        order.setUserId(userId);

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingPayment));

        when(orderRepository.findById(existingPayment.getOrderId()))
                .thenReturn(Optional.of(order));

        // Act
        PaymentTransactionService.ClaimResult result =
                paymentTransactionService.claimPayment(
                        userId,
                        new CreatePaymentRequest(
                                10L,
                                PaymentMethod.UPI
                        ),
                        idempotencyKey
                );

        // Assert
        assertFalse(result.newlyCreated());

        assertSame(
                existingPayment,
                result.payment()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderRepository, never())
                .findByIdForUpdate(anyLong());
    }


    @Test
    void shouldMarkPaymentAndOrderAsConfirmed() {

        // Arrange
        Long paymentId = 1L;

        Payment payment = new Payment();
        payment.setOrderId(10L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        Order order = new Order();
        order.setUserId(1L);
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(payment.getOrderId()))
                .thenReturn(Optional.of(order));

        // Act
        paymentTransactionService.completePayment(
                paymentId,
                true,
                "PROVIDER-123"
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

        verify(paymentRepository)
                .save(payment);

        verify(orderRepository)
                .save(order);
    }


    @Test
    void shouldMarkPaymentAndOrderAsFailed() {

        // Arrange
        Long paymentId = 1L;

        Payment payment = new Payment();
        payment.setOrderId(10L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        Order order = new Order();
        order.setUserId(1L);
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(payment.getOrderId()))
                .thenReturn(Optional.of(order));

        // Act
        paymentTransactionService.completePayment(
                paymentId,
                false,
                "PROVIDER-FAIL-123"
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

        verify(paymentRepository)
                .save(payment);

        verify(orderRepository)
                .save(order);
    }


    @Test
    void shouldIgnoreDuplicateCompletion() {

        // Arrange
        Long paymentId = 1L;

        Payment payment = new Payment();
        payment.setOrderId(10L);
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        // Act
        paymentTransactionService.completePayment(
                paymentId,
                true,
                "PROVIDER-123"
        );

        // Assert
        assertEquals(
                PaymentStatus.SUCCESS,
                payment.getStatus()
        );

        verify(orderRepository, never())
                .findByIdForUpdate(anyLong());

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }
}
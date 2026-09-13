package com.paymentguard.payment.service;

import com.paymentguard.common.exception.ApiException;
import com.paymentguard.order.entity.Order;
import com.paymentguard.order.entity.OrderStatus;
import com.paymentguard.order.repository.OrderRepository;
import com.paymentguard.payment.dto.CreatePaymentRequest;
import com.paymentguard.payment.entity.Payment;
import com.paymentguard.payment.entity.PaymentStatus;
import com.paymentguard.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ClaimResult claimPayment(
            Long userId,
            CreatePaymentRequest request,
            String idempotencyKey
    ) {

        Payment existing =
                paymentRepository.findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existing != null) {
            validateOwnership(existing, userId);
            return new ClaimResult(existing, false);
        }

        Order order = orderRepository
                .findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Order not found"
                ));

        if (!order.getUserId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "You do not have access to this order"
            );
        }

        // Double-check after acquiring the order lock.
        existing = paymentRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existing != null) {
            validateOwnership(existing, userId);
            return new ClaimResult(existing, false);
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "Order is already paid"
            );
        }

        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "Payment is already in progress for this order"
            );
        }

        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "Order cannot accept payment in current state"
            );
        }

        order.setStatus(OrderStatus.PAYMENT_PENDING);
        orderRepository.save(order);

        Payment payment = new Payment();

        payment.setOrderId(order.getId());
        payment.setAmount(order.getAmount());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPaymentMethod(request.paymentMethod());
        payment.setStatus(PaymentStatus.IN_PROGRESS);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());

        Payment saved = paymentRepository.save(payment);

        return new ClaimResult(saved, true);
    }

    @Transactional
    public void completePayment(
            Long paymentId,
            boolean success,
            String providerReference
    ) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Payment not found"
                ));

        // Idempotent completion.
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.FAILURE) {
            return;
        }

        Order order = orderRepository
                .findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Order not found"
                ));

        payment.setProviderReference(providerReference);

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            order.setStatus(OrderStatus.CONFIRMED);
        } else {
            payment.setStatus(PaymentStatus.FAILURE);
            order.setStatus(OrderStatus.PAYMENT_FAILED);
        }

        paymentRepository.save(payment);
        orderRepository.save(order);
    }

    private void validateOwnership(
            Payment payment,
            Long userId
    ) {

        Order order = orderRepository
                .findById(payment.getOrderId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Order not found"
                ));

        if (!order.getUserId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "You do not have access to this payment"
            );
        }
    }

    public record ClaimResult(
            Payment payment,
            boolean newlyCreated
    ) {
    }
}
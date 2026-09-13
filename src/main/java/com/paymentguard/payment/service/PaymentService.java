package com.paymentguard.payment.service;

import com.paymentguard.common.exception.ApiException;
import com.paymentguard.order.entity.Order;
import com.paymentguard.order.entity.OrderStatus;
import com.paymentguard.order.repository.OrderRepository;
import com.paymentguard.payment.dto.CreatePaymentRequest;
import com.paymentguard.payment.dto.PaymentResponse;
import com.paymentguard.payment.dto.ProviderWebhookRequest;
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
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProvider paymentProvider;

    @Transactional
    public PaymentResponse create(
            Long userId,
            CreatePaymentRequest request,
            String idempotencyKey
    ) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST.value(),
                    "Idempotency-Key header is required"
            );
        }

        Payment payment = claimPayment(
                userId,
                request,
                idempotencyKey
        );

        /*
         * Existing payment means this is an idempotent retry.
         */
        if (payment.getStatus() != PaymentStatus.IN_PROGRESS) {
            return toResponse(payment);
        }

        processPayment(payment.getId());

        Payment completedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Payment not found after processing"
                ));

        return toResponse(completedPayment);
    }

    protected Payment claimPayment(
            Long userId,
            CreatePaymentRequest request,
            String idempotencyKey
    ) {

        /*
         * First check:
         * Same idempotency key = same payment attempt.
         */
        var existingPayment =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {

            Payment payment = existingPayment.get();

            validatePaymentOwnership(payment, userId);

            return payment;
        }

        /*
         * Lock the order.
         *
         * This is the important concurrency protection.
         */
        Order order = orderRepository
                .findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Order not found"
                ));

        /*
         * Verify that this order belongs to the logged-in user.
         */
        if (!order.getUserId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "You do not have access to this order"
            );
        }

        /*
         * Second idempotency check after acquiring the lock.
         */
        existingPayment =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();

            validatePaymentOwnership(payment, userId);

            return payment;
        }

        /*
         * Order has already been successfully paid.
         */
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "Order is already paid"
            );
        }

        /*
         * Another payment is already being processed.
         */
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "Payment is already in progress for this order"
            );
        }

        /*
         * Only CREATED and PAYMENT_FAILED orders
         * can start a new payment attempt.
         */
        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PAYMENT_FAILED) {

            throw new ApiException(
                    HttpStatus.CONFLICT.value(),
                    "Order cannot accept payment in current state"
            );
        }

        /*
         * Claim the order.
         */
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        orderRepository.save(order);

        /*
         * Create payment in IN_PROGRESS state.
         */
        Payment payment = new Payment();

        payment.setOrderId(order.getId());
        payment.setAmount(order.getAmount());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPaymentMethod(request.paymentMethod());
        payment.setStatus(PaymentStatus.IN_PROGRESS);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());

        return paymentRepository.save(payment);
    }

    public void processPayment(Long paymentId) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Payment not found"
                ));

        /*
         * Already completed.
         */
        if (payment.getStatus() != PaymentStatus.IN_PROGRESS) {
            return;
        }

        /*
         * External provider call happens OUTSIDE
         * the database transaction.
         */
        PaymentProvider.ProviderResult result =
                paymentProvider.charge(
                        payment.getId(),
                        payment.getAmount()
                );

        completePayment(
                paymentId,
                result.success(),
                result.reference()
        );
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

        /*
         * Idempotent completion.
         */
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

    @Transactional
    public PaymentResponse webhook(ProviderWebhookRequest request) {

        Payment payment = paymentRepository
                .findByProviderReference(request.providerReference())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Payment not found for provider reference"
                ));

        /*
         * Duplicate webhook.
         */
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.FAILURE) {
            return toResponse(payment);
        }

        Order order = orderRepository
                .findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Order not found"
                ));

        if (request.status() == PaymentStatus.SUCCESS) {

            payment.setStatus(PaymentStatus.SUCCESS);
            order.setStatus(OrderStatus.CONFIRMED);

        } else if (request.status() == PaymentStatus.FAILURE) {

            payment.setStatus(PaymentStatus.FAILURE);
            order.setStatus(OrderStatus.PAYMENT_FAILED);

        } else {

            payment.setStatus(request.status());
        }

        paymentRepository.save(payment);
        orderRepository.save(order);

        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long paymentId) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "Payment not found"
                ));

        return toResponse(payment);
    }

    private void validatePaymentOwnership(
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

    private PaymentResponse toResponse(Payment payment) {

        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getCreatedAt()
        );
    }
}
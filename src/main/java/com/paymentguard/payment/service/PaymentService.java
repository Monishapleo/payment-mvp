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
    private final PaymentTransactionService paymentTransactionService;

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

        PaymentTransactionService.ClaimResult result =
                paymentTransactionService.claimPayment(
                        userId,
                        request,
                        idempotencyKey
                );

        Payment payment = result.payment();

        // Existing payment = idempotent retry.
        // NEVER call provider again.
        if (!result.newlyCreated()) {
            return toResponse(payment);
        }

        processPayment(payment.getId());

        Payment completedPayment =
                paymentRepository.findById(payment.getId())
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "Payment not found after processing"
                        ));

        return toResponse(completedPayment);
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
        paymentTransactionService.completePayment(
                paymentId,
                success,
                providerReference
        );
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
package com.paymentguard.payment.service;

import com.paymentguard.payment.dto.*;
import com.paymentguard.payment.entity.*;
import com.paymentguard.payment.repository.PaymentRepository;
import com.paymentguard.order.entity.Order;
import com.paymentguard.order.service.OrderService;
import com.paymentguard.common.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentService {
    private final PaymentRepository repo;
    private final OrderService orders;
    private final PaymentProvider provider;

    public PaymentService(PaymentRepository r, OrderService o, PaymentProvider p) {
        repo = r;
        orders = o;
        provider = p;
    }

    @Transactional
    public PaymentResponse create(Long userId, CreatePaymentRequest req, String key) {
        if (key == null || key.isBlank()) throw new ApiException(400, "Idempotency-Key header is required");
        Optional<Payment> existing = repo.findByIdempotencyKey(key);
        if (existing.isPresent()) return response(existing.get());
        Order order = orders.get(req.orderId());
        if (!order.getUserId().equals(userId)) throw new ApiException(403, "Order does not belong to user");
        if (order.getStatus() == com.paymentguard.order.entity.OrderStatus.CONFIRMED)
            throw new ApiException(409, "Order is already paid");
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setAmount(order.getAmount());
        p.setIdempotencyKey(key);
        p.setPaymentMethod(req.paymentMethod());
        p.setStatus(PaymentStatus.PENDING);
        repo.saveAndFlush(p);
        orders.markPaymentPending(order.getId());
        p.setStatus(PaymentStatus.IN_PROGRESS);
        PaymentProvider.ProviderResult result = provider.charge(p.getId(), p.getAmount());
        p.setProviderReference(result.reference());
        p.setStatus(result.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILURE);
        if (result.success()) orders.confirm(order.getId());
        else orders.paymentFailed(order.getId());
        return response(p);
    }

    @Transactional
    public PaymentResponse webhook(ProviderWebhookRequest req) {
        Payment p = repo.findByProviderReference(req.providerReference()).orElseThrow(() -> new ApiException(404, "Unknown provider reference"));
        if (p.getStatus() == PaymentStatus.SUCCESS && req.status() == PaymentStatus.FAILURE) return response(p);
        if (p.getStatus() == req.status()) return response(p);
        if (p.getStatus() == PaymentStatus.IN_PROGRESS && (req.status() == PaymentStatus.SUCCESS || req.status() == PaymentStatus.FAILURE)) {
            p.setStatus(req.status());
            if (req.status() == PaymentStatus.SUCCESS) orders.confirm(p.getOrderId());
            else orders.paymentFailed(p.getOrderId());
        }
        return response(p);
    }

    public PaymentResponse get(Long id) {
        return response(repo.findById(id).orElseThrow(() -> new ApiException(404, "Payment not found")));
    }

    private PaymentResponse response(Payment p) {
        return new PaymentResponse(p.getId(), p.getAmount(), p.getStatus(), p.getPaymentMethod(), p.getCreatedAt());
    }
}

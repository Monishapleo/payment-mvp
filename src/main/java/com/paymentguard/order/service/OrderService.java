package com.paymentguard.order.service;

import com.paymentguard.order.dto.*;
import com.paymentguard.order.entity.*;
import com.paymentguard.order.repository.OrderRepository;
import com.paymentguard.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository repo;

    public OrderService(OrderRepository r) {
        repo = r;
    }

    @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest r) {
        Order o = new Order();
        o.setUserId(userId);
        o.setAmount(r.amount());
        o.setStatus(OrderStatus.CREATED);
        repo.save(o);
        return new OrderResponse(o.getId(), o.getAmount(), o.getStatus(), o.getCreatedAt());
    }

    public Order get(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(404, "Order not found"));
    }

    public OrderResponse response(Order o) {
        return new OrderResponse(o.getId(), o.getAmount(), o.getStatus(), o.getCreatedAt());
    }

    @Transactional
    public void markPaymentPending(Long id) {
        Order o = get(id);
        o.setStatus(OrderStatus.PAYMENT_PENDING);
    }

    @Transactional
    public void confirm(Long id) {
        Order o = get(id);
        if (o.getStatus() != OrderStatus.CONFIRMED) o.setStatus(OrderStatus.CONFIRMED);
    }

    @Transactional
    public void paymentFailed(Long id) {
        Order o = get(id);
        if (o.getStatus() != OrderStatus.CONFIRMED) o.setStatus(OrderStatus.PAYMENT_FAILED);
    }
}

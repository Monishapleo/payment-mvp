package com.paymentguard.order.dto;

import com.paymentguard.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(Long orderId, BigDecimal amount, OrderStatus status, Instant createdAt) {
}

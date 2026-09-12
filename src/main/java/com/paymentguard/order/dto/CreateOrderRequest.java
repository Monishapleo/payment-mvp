package com.paymentguard.order.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record CreateOrderRequest(@DecimalMin("0.01") BigDecimal amount) {
}

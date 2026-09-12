package com.paymentguard.payment.dto;

import com.paymentguard.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(@NotNull Long orderId, @NotNull PaymentMethod paymentMethod) {
}

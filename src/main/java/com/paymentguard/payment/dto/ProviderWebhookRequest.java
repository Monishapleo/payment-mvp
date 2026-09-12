package com.paymentguard.payment.dto;

import com.paymentguard.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderWebhookRequest(@NotBlank String providerReference, @NotNull PaymentStatus status) {
}

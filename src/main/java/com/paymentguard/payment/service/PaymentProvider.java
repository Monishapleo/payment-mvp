package com.paymentguard.payment.service;

import java.math.BigDecimal;

public interface PaymentProvider {
    ProviderResult charge(Long paymentId, BigDecimal amount);

    ProviderResult getStatus(String providerReference);

    record ProviderResult(String reference, boolean success) {
    }
}

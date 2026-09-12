package com.paymentguard.payment.dto;

import com.paymentguard.payment.entity.*;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long paymentId, BigDecimal amount, PaymentStatus status, PaymentMethod paymentMethod,
                              Instant createdDate) {
}

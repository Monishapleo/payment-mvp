package com.paymentguard.payment.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "order_id", nullable = false)
    Long orderId;
    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal amount;
    @Column(name = "idempotency_key", nullable = false, unique = true)
    String idempotencyKey;
    @Column(name = "provider_reference", unique = true)
    String providerReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PaymentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    PaymentMethod paymentMethod;
    @Version
    Long version;
    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long v) {
        orderId = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        amount = v;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String v) {
        idempotencyKey = v;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String v) {
        providerReference = v;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus v) {
        status = v;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod v) {
        paymentMethod = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

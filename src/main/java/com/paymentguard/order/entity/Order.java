package com.paymentguard.order.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "user_id", nullable = false)
    Long userId;
    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OrderStatus status;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long v) {
        userId = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        amount = v;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus v) {
        status = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

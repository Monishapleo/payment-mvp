# PaymentGuard

A Java/Spring Boot backend for reliable payment processing, designed around idempotency, state transitions, webhook handling, reconciliation, and reliable event delivery.

## Current milestone
- Modular monolith with User, Order, and Payment modules
- PostgreSQL + Flyway
- REST APIs
- JWT authentication
- Idempotency-key uniqueness
- Payment provider simulation
- Webhook status handling
- Optimistic locking on Order/Payment
- Kafka topic foundation
- Docker Compose for PostgreSQL and Kafka
- Transactional outbox schema prepared for the next milestone

## Run
```bash
docker compose up -d
mvn spring-boot:run
```

Register/login first, then use the JWT as `Authorization: Bearer <token>` for order/payment APIs.

## Core APIs
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/orders`
- `GET /api/orders/{id}`
- `POST /api/payments` with `Idempotency-Key`
- `GET /api/payments/{id}`
- `POST /api/payments/webhook`

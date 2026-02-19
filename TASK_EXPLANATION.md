# Payment Gateway Challenge - Implementation Explanation

## Overview

This document explains the implementation of a **Payment Gateway API** built as a recruitment challenge for Checkout.com. The application is a Spring Boot 3.3.0 service that simulates a payment processing system, acting as an intermediary between merchants and acquiring banks (payment providers).

## What Was Built

A fully functional payment gateway REST API with the following capabilities:

### 1. **Payment Processing Endpoint** (`POST /payments`)
Accepts payment requests from merchants with card details and processes them through acquiring banks (payment providers).

**Key Features:**
- Card validation (number, CVV, expiry date)
- Multi-provider support (SIMULATOR, STRIPE)
- Idempotency protection to prevent duplicate payments
- Card number masking for security (only last 4 digits returned)
- Automatic authorization status determination based on bank response

### 2. **Payment Retrieval Endpoint** (`GET /payments/{id}`)
Allows merchants to retrieve previously processed payment details by payment ID.

**Key Features:**
- UUID-based payment lookup
- Returns masked card information
- 404 error handling for non-existent payments

### 3. **Idempotency Mechanism**
Prevents duplicate payment processing when network issues cause request retries.

**How It Works:**
- Clients send an optional `Idempotency-Key` header with payment requests
- If the same key is used with the same request body, the cached response is returned without reprocessing
- If the same key is used with a different request body, a 409 Conflict error is returned
- Protects against accidental duplicate charges

### 4. **Multi-Provider Support (Strategy Pattern)**
Flexible architecture that supports multiple payment providers through a strategy pattern implementation.

**Implemented Providers:**
- **SimulatorBankClient**: Connects to an external bank simulator (Mountebank mock) via HTTP
- **MockStripeClient**: Mock implementation for Stripe payments (can be replaced with real Stripe integration)

**Provider Selection:**
- Specified in the `provider` field of the payment request
- Defaults to "SIMULATOR" if not provided
- Factory pattern automatically routes to the correct client

## Technical Architecture

### Layered Architecture

```
┌─────────────────────────────────────────┐
│    PaymentGatewayController (REST)      │
│  - POST /payments                        │
│  - GET /payments/{id}                    │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    PaymentGatewayService (Business)     │
│  - Payment processing logic              │
│  - Idempotency checking                  │
│  - Card masking                          │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌─────────────┐  ┌──────────────────┐
│ PaymentsRepo│  │ BankClientFactory│
│             │  │  (Strategy)      │
│ - Stores    │  └────────┬─────────┘
│   payments  │           │
│ - Idem keys │    ┌──────┴──────┐
└─────────────┘    ▼             ▼
            ┌────────────┐  ┌──────────┐
            │ Simulator  │  │  Stripe  │
            │   Client   │  │  Client  │
            └────────────┘  └──────────┘
```

### Key Design Patterns

#### 1. **Strategy Pattern** (Payment Providers)
- **Interface**: `AcquiringBankClient` defines the contract for payment processors
- **Implementations**: `SimulatorBankClient`, `MockStripeClient`
- **Factory**: `BankClientFactory` selects the appropriate implementation based on provider name
- **Benefits**: Easy to add new payment providers without modifying existing code

#### 2. **Repository Pattern** (Data Access)
- **Class**: `PaymentsRepository`
- **Storage**: In-memory `ConcurrentHashMap` for thread-safe operations
- **Dual Indexing**: Payments stored by UUID and by idempotency key
- **Benefits**: Abstracts data access, easily replaceable with database implementation

#### 3. **Record Types** (Java 17)
- **Models**: `PostPaymentRequest`, `PostPaymentResponse`, `GetPaymentResponse`, `Payment`, `BankRequest`, `BankResponse`
- **Benefits**: Immutable data carriers with automatic equals/hashCode, perfect for DTOs

#### 4. **Centralized Exception Handling**
- **Class**: `CommonExceptionHandler` (annotated with `@ControllerAdvice`)
- **Custom Exceptions**: `EventProcessingException`, `IdempotencyConflictException`
- **Benefits**: Consistent error responses across all endpoints

## Payment Processing Flow

### Happy Path Flow

```
1. Merchant → POST /payments with card details + Idempotency-Key
                    ↓
2. Controller validates input (Jakarta Bean Validation)
                    ↓
3. Service checks idempotency cache
   - If key exists with same body → return cached response
   - If key exists with different body → throw 409 Conflict
                    ↓
4. Service creates BankRequest
                    ↓
5. BankClientFactory selects appropriate client
                    ↓
6. Client sends request to acquiring bank
                    ↓
7. Bank returns authorization result
                    ↓
8. Service creates Payment record with status (AUTHORIZED/DECLINED)
                    ↓
9. Service stores payment in repository
                    ↓
10. Service stores idempotency key mapping
                    ↓
11. Service masks card number (last 4 digits only)
                    ↓
12. Controller returns 201 Created with PostPaymentResponse
```

### Idempotency Flow

```
Request with Idempotency-Key "key-123"
                    ↓
Check cache for "key-123"
        ↓                    ↓
    NOT FOUND            FOUND
        ↓                    ↓
  Process payment      Compare request bodies
        ↓                    ↓
  Store result        Same    Different
        ↓              ↓         ↓
  Cache key-123   Return    409 Conflict
                  cached
                  result
```

## Data Models

### Request/Response Models

**PostPaymentRequest** (Input)
```json
{
  "card_number": "1234567890123456",    // 14-19 digits
  "expiry_month": 12,                    // 1-12
  "expiry_year": 2025,                   // >= 2024, must be future
  "currency": "USD",                     // 3-letter ISO code
  "amount": 100,                         // Positive integer
  "cvv": "123",                          // 3-4 digits
  "provider": "SIMULATOR"                // Optional (defaults to SIMULATOR)
}
```

**PostPaymentResponse** (Output)
```json
{
  "id": "uuid-here",
  "status": "Authorized",                // or "Declined"
  "last_four_card_digits": "3456",       // Masked card number
  "expiry_month": 12,
  "expiry_year": 2025,
  "currency": "USD",
  "amount": 100
}
```

### Internal Models

**Payment** (Stored Entity)
- Full card details (unmasked internally)
- Payment status (AUTHORIZED/DECLINED)
- Original request for idempotency comparison
- UUID identifier

**BankRequest/BankResponse** (Bank Communication)
- `BankRequest`: Card details formatted for bank API
- `BankResponse`: Contains `authorized` boolean and authorization code

## Security Features

### 1. **Card Number Masking**
- Full card numbers are stored internally but never returned to clients
- Only the last 4 digits are exposed in API responses
- Method: `maskCardNumber()` in `PaymentGatewayService`

### 2. **Input Validation**
- Jakarta Bean Validation annotations on request models
- Card number: 14-19 digit pattern
- CVV: 3-4 digit pattern
- Expiry date: Must be in the future
- Currency: 3-letter ISO code pattern

### 3. **Error Handling**
- Bank errors properly caught and converted to meaningful HTTP responses
- 4xx/5xx from bank → EventProcessingException
- No sensitive error details leaked to clients

## Testing Strategy

### Integration Tests (`PaymentGatewayControllerTest`)

The test suite uses Spring Boot Test with MockMvc for full integration testing:

#### Test Coverage

1. **`whenPaymentWithIdExistThenCorrectPaymentIsReturned`**
   - Verifies successful payment retrieval by ID
   - Validates card masking (last 4 digits)
   - Ensures correct status mapping

2. **`whenPaymentWithIdDoesNotExistThen404IsReturned`**
   - Tests error handling for non-existent payments
   - Validates 404 response

3. **`whenProcessingPaymentWithIdempotencyKey_SameKeyReturnsSameResponseWithoutProcessing`**
   - Core idempotency test
   - Verifies same key + same body = cached response
   - Confirms bank is NOT called twice (using Mockito verification)

4. **`whenProcessingPaymentWithIdempotencyKey_SameKeyDifferentBodyReturns409Conflict`**
   - Idempotency conflict test
   - Verifies same key + different body = 409 Conflict
   - Ensures second request is rejected

5. **`whenProviderIsStripe_ThenStripeClientIsUsed`**
   - Strategy pattern validation
   - Verifies correct client selection based on provider field
   - Tests provider routing logic

### Test Approach

- **Mocking**: Bank clients are mocked to avoid external dependencies
- **Repository**: Real in-memory repository used for realistic state management
- **Assertions**: JSON path assertions for response validation
- **Mockito Verification**: Ensures methods are called the correct number of times

## Infrastructure

### Bank Simulator (Mountebank)

- **Container**: `bbyars/mountebank:2.8.1`
- **Purpose**: Simulates acquiring bank responses for testing
- **Configuration**: `imposters/bank_simulator.ejs` defines mock responses
- **Ports**: 
  - 2525: Mountebank admin
  - 8080: Bank simulator API

### Application Configuration

**application.properties**
```properties
server.port=8081                        # Application runs on 8081
springdoc.swagger-ui.enabled=true       # Swagger UI enabled
springdoc.api-docs.enabled=true         # OpenAPI docs enabled
bank.simulator.url=http://localhost:8080/payments  # Bank endpoint
```

## API Documentation

### OpenAPI/Swagger Integration

- **Library**: SpringDoc OpenAPI 2.5.0
- **UI Location**: `http://localhost:8081/swagger-ui/index.html`
- **Features**:
  - Interactive API testing
  - Request/response schema documentation
  - Example payloads
  - Validation rules displayed

## Running the Application

### Prerequisites
- JDK 17
- Docker (for bank simulator)

### Steps

1. **Start Bank Simulator**
   ```bash
   docker-compose up -d
   ```

2. **Run Application**
   ```bash
   ./gradlew bootRun
   ```

3. **Access Swagger UI**
   ```
   http://localhost:8081/swagger-ui/index.html
   ```

4. **Run Tests**
   ```bash
   ./gradlew test
   ```

## Code Quality Standards

### EditorConfig Rules
- **Indentation**: 2 spaces for Java
- **Line Length**: 100 characters max
- **Encoding**: UTF-8
- **Brace Style**: End-of-line (`{` on same line)
- **Import Order**: `java.*` before `javax.*`, single class imports

### Validation Standards
- Bean Validation (Jakarta) for request validation
- Meaningful error messages
- Consistent response structure

## Key Technical Decisions

### 1. **In-Memory Storage**
- **Choice**: `ConcurrentHashMap` for payments and idempotency keys
- **Rationale**: Simplicity for prototype, thread-safe operations
- **Production Consideration**: Would need database (PostgreSQL, etc.)

### 2. **Spring RestClient**
- **Choice**: Spring's modern `RestClient` (not deprecated `RestTemplate`)
- **Rationale**: More modern, fluent API, better error handling
- **Usage**: HTTP calls to bank simulator

### 3. **Java Records**
- **Choice**: Java 17 record types for all DTOs
- **Rationale**: Immutability, automatic equals/hashCode, concise syntax
- **Benefits**: Type safety, reduced boilerplate

### 4. **Strategy Pattern for Providers**
- **Choice**: Interface-based provider abstraction
- **Rationale**: Easy to add new providers (Stripe, PayPal, etc.)
- **Benefits**: Open/Closed Principle, testability

### 5. **Idempotency via Headers**
- **Choice**: Optional `Idempotency-Key` header (standard practice)
- **Rationale**: Follows HTTP idempotency best practices
- **Implementation**: Request body comparison for conflict detection

## Potential Production Enhancements

While this is a solid prototype, production deployment would require:

1. **Database Integration**
   - Replace `ConcurrentHashMap` with JPA/Hibernate
   - PostgreSQL or MySQL for persistence
   - Migration scripts (Flyway/Liquibase)

2. **Security Hardening**
   - Card data encryption at rest
   - TLS for all communications
   - PCI DSS compliance measures
   - API authentication (OAuth2/JWT)

3. **Observability**
   - Structured logging (JSON format)
   - Metrics (Micrometer/Prometheus)
   - Distributed tracing (OpenTelemetry)
   - Health checks and monitoring

4. **Resilience**
   - Circuit breakers (Resilience4j) for bank calls
   - Retry policies with exponential backoff
   - Request timeout configuration
   - Rate limiting

5. **Additional Features**
   - Payment refunds
   - Partial captures
   - Payment history pagination
   - Webhook support for async notifications
   - Audit logging

## Summary

This Payment Gateway implementation demonstrates:

- ✅ **Clean Architecture**: Layered design with clear separation of concerns
- ✅ **Design Patterns**: Strategy, Repository, Factory patterns properly applied
- ✅ **Modern Java**: Leverages Java 17 features (records, text blocks)
- ✅ **Spring Boot Best Practices**: Proper use of dependency injection, configuration
- ✅ **Idempotency**: Robust duplicate prevention mechanism
- ✅ **Testability**: Comprehensive integration tests with mocking
- ✅ **API Documentation**: Interactive Swagger UI for API exploration
- ✅ **Extensibility**: Easy to add new payment providers
- ✅ **Security**: Card masking, input validation, error handling

The implementation successfully simulates a payment gateway system with production-ready patterns, making it an excellent demonstration of Java/Spring Boot development skills.

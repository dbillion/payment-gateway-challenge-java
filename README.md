# Payment Gateway Challenge - Java

A Spring Boot 3.3.0 payment gateway application built with JDK 17. This is a recruitment challenge project from Checkout.com that simulates a payment processing system.

## 📋 Features

- **Payment Processing**: Accept and process payment requests with card details
- **Multiple Payment Providers**: Support for different acquiring banks (SIMULATOR, STRIPE)
- **Idempotency**: Prevent duplicate payments using idempotency keys
- **In-Memory Storage**: Uses `ConcurrentHashMap`-based repository for payments
- **OpenAPI Documentation**: Swagger UI available at runtime

## 🏗️ Architecture

The application follows a layered architecture:

```
Controller → Service → Repository
                 ↓
            Bank Client (Strategy Pattern)
```

### Key Components

| Component | Description |
|-----------|-------------|
| `PaymentGatewayController` | REST API endpoints |
| `PaymentGatewayService` | Business logic, idempotency handling |
| `PaymentsRepository` | In-memory payment storage |
| `BankClientFactory` + `AcquiringBankClient` | Provider abstraction (Strategy Pattern) |
| `SimulatorBankClient` | Calls external bank simulator via HTTP |
| `MockStripeClient` | Mock implementation for Stripe |

## 🚀 Getting Started

### Prerequisites

- JDK 17
- Docker (for bank simulator)

### Installation

1. Clone the repository
2. Start the bank simulator:
   ```bash
   docker-compose up -d
   ```

3. Run the application:
   ```bash
   ./gradlew bootRun
   ```

The application runs on **port 8081**.

## 📡 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/payments` | Process a payment |
| `GET` | `/payments/{id}` | Retrieve payment by ID |

### Example Payment Request

```bash
curl -X POST http://localhost:8081/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "card_number": "1234567890123456",
    "expiry_month": 12,
    "expiry_year": 2025,
    "currency": "USD",
    "amount": 100,
    "cvv": "123",
    "provider": "SIMULATOR"
  }'
```

## 📖 OpenAPI Documentation (Swagger UI)

The OpenAPI documentation is available at:

**http://localhost:8081/swagger-ui/index.html**

### OpenAPI Configuration

The OpenAPI key/configuration can be found in:
- **File**: `src/main/resources/application.properties`
- **Configuration Class**: `src/main/java/com/checkout/payment/gateway/configuration/ApplicationConfiguration.java`

Key properties:
```properties
server.port=8081
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
```

## 🧪 Running Tests

The project includes comprehensive integration tests using JUnit 5 and Spring Boot Test.

### Run Tests

```bash
./gradlew test
```

### Test Coverage

The test suite (`PaymentGatewayControllerTest.java`) covers:

| Test | Description |
|------|-------------|
| `whenPaymentWithIdExistThenCorrectPaymentIsReturned` | Verifies retrieving an existing payment by ID |
| `whenPaymentWithIdDoesNotExistThen404IsReturned` | Verifies 404 response for non-existent payment IDs |
| `whenProcessingPaymentWithIdempotencyKey_SameKeyReturnsSameResponseWithoutProcessing` | Tests idempotency - same key + same body returns cached response without re-processing |
| `whenProcessingPaymentWithIdempotencyKey_SameKeyDifferentBodyReturns409Conflict` | Tests idempotency conflict - same key + different body returns 409 |
| `whenProviderIsStripe_ThenStripeClientIsUsed` | Verifies the Strategy pattern correctly selects the Stripe provider |

### Test Location

```
src/test/java/com/checkout/payment/gateway/controller/PaymentGatewayControllerTest.java
```

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/checkout/payment/gateway/
│   │   ├── controller/      # REST controllers
│   │   ├── service/         # Business logic
│   │   ├── repository/      # Data access
│   │   ├── model/           # Records/DTOs
│   │   ├── client/          # External service clients
│   │   ├── exception/       # Exception classes + handlers
│   │   ├── enums/           # Enumerations
│   │   └── configuration/   # Spring configuration
│   └── resources/
│       └── application.properties
└── test/
    └── java/.../controller/ # Integration tests
```

## 🔧 Configuration

### application.properties

```properties
server.port=8081
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
```

### Bank Simulator

Default URL: `http://localhost:8080/payments` (configurable via `bank.simulator.url`)

## 📐 Development Conventions

### Code Style

- **EditorConfig**: Rules defined in `.editorconfig`
- **Indentation**: 2 spaces for Java
- **Line Length**: 100 characters max
- **Brace Style**: End-of-line (`{` on same line)
- **Imports**: Single class imports, `java.*` before `javax.*`

### Key Design Patterns

- **Strategy Pattern**: `AcquiringBankClient` interface with multiple implementations
- **Record Types**: Immutable data carriers (Java 17 records)
- **Exception Handling**: Centralized via `@ControllerAdvice`

### Validation Rules

- Card number: 14-19 digits
- CVV: 3-4 digits
- Currency: 3-letter ISO code
- Amount: Positive integer
- Expiry: Month 1-12, year >= 2024, must be in future

## 📦 Dependencies

- Spring Boot 3.3.0 (Web, Validation)
- SpringDoc OpenAPI 2.5.0
- JUnit 5 + Spring Boot Test

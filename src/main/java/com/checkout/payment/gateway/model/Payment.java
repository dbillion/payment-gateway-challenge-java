package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record Payment(
    UUID id,
    String cardNumber,
    int expiryMonth,
    int expiryYear,
    String currency,
    int amount,
    String cvv,
    PaymentStatus status,
    PostPaymentRequest originalRequest // Store the original request for comparison
) {}

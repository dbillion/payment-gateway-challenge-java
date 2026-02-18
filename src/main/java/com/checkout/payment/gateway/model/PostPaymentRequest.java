package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.YearMonth;

public record PostPaymentRequest(
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{14,19}$", message = "Card number must be between 14 and 19 digits")
    @JsonProperty("card_number")
    String cardNumber,

    @NotNull(message = "Expiry month is required")
    @Min(value = 1, message = "Expiry month must be between 1 and 12")
    @Max(value = 12, message = "Expiry month must be between 1 and 12")
    @JsonProperty("expiry_month")
    Integer expiryMonth,

    @NotNull(message = "Expiry year is required")
    @Min(value = 2024, message = "Expiry year must be in the future")
    @JsonProperty("expiry_year")
    Integer expiryYear,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    String currency,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    Integer amount,

    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
    String cvv,

    String provider // Optional field for provider selection (e.g., STRIPE, SIMULATOR)
) {
    
    public boolean isExpiryDateValid() {
        if (expiryMonth == null || expiryYear == null) return false;
        try {
            YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
            return expiry.isAfter(YearMonth.now());
        } catch (Exception e) {
            return false;
        }
    }
}

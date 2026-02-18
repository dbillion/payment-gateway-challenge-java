package com.checkout.payment.gateway.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankResponse(
    boolean authorized,
    @JsonProperty("authorization_code")
    String authorizationCode
) {}

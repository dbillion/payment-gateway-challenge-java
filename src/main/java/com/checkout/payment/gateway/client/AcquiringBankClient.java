package com.checkout.payment.gateway.client;

public interface AcquiringBankClient {
  BankResponse processPayment(BankRequest request);
  boolean supports(String provider);
}

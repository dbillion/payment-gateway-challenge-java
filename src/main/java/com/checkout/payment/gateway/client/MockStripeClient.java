package com.checkout.payment.gateway.client;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockStripeClient implements AcquiringBankClient {

  private static final Logger LOG = LoggerFactory.getLogger(MockStripeClient.class);

  @Override
  public BankResponse processPayment(BankRequest request) {
    LOG.info("Processing payment via Mock Stripe: {}", request.cardNumber().substring(request.cardNumber().length() - 4));
    // Simulate success
    return new BankResponse(true, "stripe_" + UUID.randomUUID());
  }

  @Override
  public boolean supports(String provider) {
    return "STRIPE".equalsIgnoreCase(provider);
  }
}

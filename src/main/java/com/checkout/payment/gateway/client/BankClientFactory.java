package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.EventProcessingException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BankClientFactory {

  private final List<AcquiringBankClient> clients;

  public BankClientFactory(List<AcquiringBankClient> clients) {
    this.clients = clients;
  }

  public AcquiringBankClient getClient(String provider) {
    String targetProvider = provider == null ? "SIMULATOR" : provider;
    return clients.stream()
        .filter(client -> client.supports(targetProvider))
        .findFirst()
        .orElseThrow(() -> new EventProcessingException("Unsupported payment provider: " + targetProvider));
  }
}

package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.EventProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SimulatorBankClient implements AcquiringBankClient {

  private static final Logger LOG = LoggerFactory.getLogger(SimulatorBankClient.class);
  private final RestClient restClient;
  private final String bankUrl;

  public SimulatorBankClient(RestClient restClient, @Value("${bank.simulator.url:http://localhost:8080/payments}") String bankUrl) {
    this.restClient = restClient;
    this.bankUrl = bankUrl;
  }

  @Override
  public BankResponse processPayment(BankRequest request) {
    LOG.info("Sending payment request to simulator bank: {}", request.cardNumber().substring(request.cardNumber().length() - 4));
    
    return restClient.post()
        .uri(bankUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            throw new EventProcessingException("Bank rejected the request (Bad Request)");
        })
        .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
             throw new EventProcessingException("Bank service unavailable");
        })
        .body(BankResponse.class);
  }

  @Override
  public boolean supports(String provider) {
      return "SIMULATOR".equalsIgnoreCase(provider);
  }
}

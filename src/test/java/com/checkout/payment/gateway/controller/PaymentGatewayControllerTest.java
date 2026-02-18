package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.client.BankClientFactory;
import com.checkout.payment.gateway.client.BankResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  
  @MockBean
  private BankClientFactory bankClientFactory;
  
  private AcquiringBankClient bankClient;

  @BeforeEach
  void setup() {
      bankClient = Mockito.mock(AcquiringBankClient.class);
      when(bankClientFactory.getClient(any())).thenReturn(bankClient);
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    UUID id = UUID.randomUUID();
    PostPaymentRequest originalRequest = new PostPaymentRequest(
        "1234567890123456", 12, 2024, "USD", 10, "123", "SIMULATOR");

    Payment payment = new Payment(
        id,
        "1234567890123456",
        12,
        2024,
        "USD",
        10,
        "123",
        PaymentStatus.AUTHORIZED,
        originalRequest
    );

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payments/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.status().getName()))
        .andExpect(jsonPath("$.last_four_card_digits").value("3456"));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payments/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void whenProcessingPaymentWithIdempotencyKey_SameKeyReturnsSameResponseWithoutProcessing() throws Exception {
    String idempotencyKey = "unique-key-123";
    when(bankClient.processPayment(any())).thenReturn(new BankResponse(true, "auth-1"));

    // First request
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "1234567890123456",
                  "expiry_month": 12,
                  "expiry_year": 2025,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": "123",
                  "provider": "SIMULATOR"
                }
                """))
        .andExpect(status().isCreated());

    verify(bankClient, times(1)).processPayment(any());

    // Second request with same key AND SAME BODY
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "1234567890123456",
                  "expiry_month": 12,
                  "expiry_year": 2025,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": "123",
                  "provider": "SIMULATOR"
                }
                """))
        .andExpect(status().isCreated());

    // Verify bank service was NOT called a second time
    verify(bankClient, times(1)).processPayment(any());
  }

  @Test
  void whenProcessingPaymentWithIdempotencyKey_SameKeyDifferentBodyReturns409Conflict() throws Exception {
    String idempotencyKey = "conflict-key-123";
    when(bankClient.processPayment(any())).thenReturn(new BankResponse(true, "auth-1"));

    // First request
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "1234567890123456",
                  "expiry_month": 12,
                  "expiry_year": 2025,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": "123",
                  "provider": "SIMULATOR"
                }
                """))
        .andExpect(status().isCreated());

    // Second request with same key BUT DIFFERENT BODY (amount 200 instead of 100)
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "card_number": "1234567890123456",
                  "expiry_month": 12,
                  "expiry_year": 2025,
                  "currency": "USD",
                  "amount": 200,
                  "cvv": "123",
                  "provider": "SIMULATOR"
                }
                """))
        .andExpect(status().isConflict());

    verify(bankClient, times(1)).processPayment(any());
  }
  
  @Test
  void whenProviderIsStripe_ThenStripeClientIsUsed() throws Exception {
      when(bankClientFactory.getClient("STRIPE")).thenReturn(bankClient);
      when(bankClient.processPayment(any())).thenReturn(new BankResponse(true, "stripe-auth-1"));

      mvc.perform(MockMvcRequestBuilders.post("/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {
                    "card_number": "1234567890123456",
                    "expiry_month": 12,
                    "expiry_year": 2025,
                    "currency": "USD",
                    "amount": 100,
                    "cvv": "123",
                    "provider": "STRIPE"
                  }
                  """))
          .andExpect(status().isCreated());
          
      verify(bankClientFactory).getClient("STRIPE");
      verify(bankClient).processPayment(any());
  }
}

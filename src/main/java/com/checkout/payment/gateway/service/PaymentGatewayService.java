package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.client.BankClientFactory;
import com.checkout.payment.gateway.client.BankRequest;
import com.checkout.payment.gateway.client.BankResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankClientFactory bankClientFactory;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, BankClientFactory bankClientFactory) {
    this.paymentsRepository = paymentsRepository;
    this.bankClientFactory = bankClientFactory;
  }

  public GetPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id)
        .map(this::mapToGetResponse)
        .orElseThrow(() -> new EventProcessingException("Payment not found with ID: " + id));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest request, String idempotencyKey) {
    LOG.info("Processing payment for amount: {} {}, provider: {}, idempotencyKey: {}", request.amount(), request.currency(), request.provider(), idempotencyKey);

    if (idempotencyKey != null) {
        Optional<Payment> existingPayment = paymentsRepository.getByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            if (payment.originalRequest().equals(request)) {
                LOG.info("Idempotency match found for key: {}", idempotencyKey);
                return mapToPostResponse(payment);
            } else {
                throw new IdempotencyConflictException("Idempotency key reused for different request");
            }
        }
    }

    BankRequest bankRequest = new BankRequest(
        request.cardNumber(),
        String.format("%02d/%d", request.expiryMonth(), request.expiryYear()),
        request.currency(),
        request.amount(),
        request.cvv()
    );

    AcquiringBankClient client = bankClientFactory.getClient(request.provider());

    PaymentStatus status;
    try {
        BankResponse bankResponse = client.processPayment(bankRequest);
        status = bankResponse.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
    } catch (EventProcessingException e) {
        LOG.error("Bank processing failed: {}", e.getMessage());
        throw e;
    }

    UUID id = UUID.randomUUID();
    Payment payment = new Payment(
        id,
        request.cardNumber(),
        request.expiryMonth(),
        request.expiryYear(),
        request.currency(),
        request.amount(),
        request.cvv(),
        status,
        request
    );

    paymentsRepository.add(payment);
    if (idempotencyKey != null) {
        paymentsRepository.addIdempotencyKey(idempotencyKey, payment);
    }

    return mapToPostResponse(payment);
  }

  private PostPaymentResponse mapToPostResponse(Payment payment) {
    return new PostPaymentResponse(
        payment.id(),
        payment.status(),
        maskCardNumber(payment.cardNumber()),
        payment.expiryMonth(),
        payment.expiryYear(),
        payment.currency(),
        payment.amount()
    );
  }

  private GetPaymentResponse mapToGetResponse(Payment payment) {
    return new GetPaymentResponse(
        payment.id(),
        payment.status(),
        maskCardNumber(payment.cardNumber()),
        payment.expiryMonth(),
        payment.expiryYear(),
        payment.currency(),
        payment.amount()
    );
  }

  private String maskCardNumber(String cardNumber) {
    if (cardNumber == null || cardNumber.length() < 4) {
        return "****";
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }
}

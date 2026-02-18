package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.Payment;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final ConcurrentHashMap<UUID, Payment> payments = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Payment> idempotencyKeys = new ConcurrentHashMap<>();

  public void add(Payment payment) {
    payments.put(payment.id(), payment);
  }

  public void addIdempotencyKey(String key, Payment payment) {
    if (key != null) {
        idempotencyKeys.put(key, payment);
    }
  }

  public Optional<Payment> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

  public Optional<Payment> getByIdempotencyKey(String key) {
    return Optional.ofNullable(idempotencyKeys.get(key));
  }
}

package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentUserService {
    private final PaymentRepository paymentRepository;

    public Payment savePayment(Payment payment) {
        return paymentRepository.save(payment);
    }

    public void deleteUserPayments(User user) {
        paymentRepository.deleteByUser(user);
    }
}
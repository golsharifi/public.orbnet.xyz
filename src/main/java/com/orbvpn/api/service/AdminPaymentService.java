package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.TransactionPage;
import com.orbvpn.api.domain.dto.TransactionView;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentService {
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public TransactionPage getAllTransactions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> paymentsPage = paymentRepository.findAll(pageable);

        return TransactionPage.builder()
                .content(paymentsPage.getContent().stream()
                        .map(this::mapToTransactionView)
                        .collect(Collectors.toList()))
                .totalElements((int) paymentsPage.getTotalElements())
                .size(paymentsPage.getSize())
                .number(paymentsPage.getNumber())
                .build();
    }

    private TransactionView mapToTransactionView(Payment payment) {
        String description = buildDescription(payment);

        return TransactionView.builder()
                .id(String.valueOf(payment.getId()))
                .userId(payment.getUser() != null ? String.valueOf(payment.getUser().getId()) : null)
                .amount(payment.getPrice() != null ? payment.getPrice() : BigDecimal.ZERO)
                .currency("USD") // Default currency
                .status(payment.getStatus() != null ? payment.getStatus().name() : "UNKNOWN")
                .gateway(payment.getGateway() != null ? payment.getGateway().name() : "UNKNOWN")
                .transactionDate(payment.getCreatedAt())
                .description(description)
                .build();
    }

    private String buildDescription(Payment payment) {
        StringBuilder desc = new StringBuilder();

        if (payment.getCategory() != null) {
            desc.append(payment.getCategory().name()).append(" - ");
        }

        if (payment.getGroupId() > 0) {
            desc.append("Group ID: ").append(payment.getGroupId());
        } else if (payment.getMoreLoginCount() > 0) {
            desc.append("Extra logins: ").append(payment.getMoreLoginCount());
        } else {
            desc.append("Subscription payment");
        }

        return desc.toString();
    }
}

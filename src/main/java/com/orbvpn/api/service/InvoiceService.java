package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.InvoiceUpdate;
import com.orbvpn.api.domain.entity.Invoice;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserProfile;
import com.orbvpn.api.domain.enums.PaymentCategory;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.InvoiceRepository;
import com.orbvpn.api.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final UserProfileRepository userProfileRepository;

    // Invoice status constants
    public static final String INVOICE_STATUS_PAID = "PAID";
    public static final String INVOICE_STATUS_PENDING = "PENDING";
    public static final String INVOICE_STATUS_REFUNDED = "REFUNDED";
    public static final String INVOICE_STATUS_CANCELLED = "CANCELLED";

    public void createInvoice(Payment payment) {
        User user = payment.getUser();
        UserProfile profile = userProfileRepository.findByUser(user)
                .orElse(null);
        if ( profile == null )
            throw new NotFoundException(String.format("User Profile could not found for the user %d.", user.getId()));

        Invoice invoice = Invoice.builder()
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .address(profile.getAddress())
                .phoneNumber(profile.getPhone())
                .email(user.getEmail())
                .paymentDate(payment.getCreatedAt())
                .paymentMethod(payment.getGateway())
                .totalAmount(payment.getPrice())
                .payment(payment)
                .status(mapPaymentStatusToInvoiceStatus(payment.getStatus()))
                .build();

        if (payment.getCategory().equals(PaymentCategory.GROUP)) {
            invoice.setAmountForGroup(payment.getPrice());
            invoice.setGroupId(payment.getGroupId());
        } else  if (payment.getCategory().equals(PaymentCategory.MORE_LOGIN)) {
            invoice.setAmountForMultiLogin(payment.getPrice());
            invoice.setMultiLogin(payment.getMoreLoginCount());
        }

        invoiceRepository.save(invoice);
        log.info("Invoice created for payment {} with status {}", payment.getId(), invoice.getStatus());
    }

    /**
     * Update invoice status based on payment status change
     */
    @Transactional
    public void updateInvoiceStatusFromPayment(Payment payment) {
        Invoice invoice = invoiceRepository.findByPaymentId(payment.getId()).orElse(null);
        if (invoice == null) {
            log.debug("No invoice found for payment {}", payment.getId());
            return;
        }

        String newStatus = mapPaymentStatusToInvoiceStatus(payment.getStatus());
        if (!newStatus.equals(invoice.getStatus())) {
            String oldStatus = invoice.getStatus();
            invoice.setStatus(newStatus);
            invoiceRepository.save(invoice);
            log.info("Invoice {} status updated from {} to {} (payment {})",
                    invoice.getId(), oldStatus, newStatus, payment.getId());
        }
    }

    /**
     * Map payment status to invoice status
     */
    private String mapPaymentStatusToInvoiceStatus(PaymentStatus paymentStatus) {
        if (paymentStatus == null) {
            return INVOICE_STATUS_PENDING;
        }

        return switch (paymentStatus) {
            case SUCCEEDED -> INVOICE_STATUS_PAID;
            case REFUNDED -> INVOICE_STATUS_REFUNDED;
            case FAILED, EXPIRED -> INVOICE_STATUS_CANCELLED;
            default -> INVOICE_STATUS_PENDING;
        };
    }

    public List<Invoice> getAll() {
        return invoiceRepository.findAll();
    }

    public Invoice getById(Integer id) {
        return invoiceRepository.findById(id)
                .orElse(null);
    }

    public Invoice getByPaymentId(int paymentId) {
        return invoiceRepository.findByPaymentId(paymentId)
                .orElse(null);
    }

    public Invoice updateInvoice(InvoiceUpdate invoiceUpdate) {
        Invoice invoice = invoiceRepository.findById(invoiceUpdate.getId())
                .orElseThrow();
        invoice = invoiceUpdate.updateInvoice(invoice);
        invoiceRepository.save(invoice);
        return invoice;
    }

    public List<Invoice> getByDateRange(String _beginDate, String _endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDateTime beginDate = LocalDate.parse(_beginDate, formatter).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(_endDate, formatter).atStartOfDay();
        return invoiceRepository.getByDateRange(beginDate,endDate);
    }
}

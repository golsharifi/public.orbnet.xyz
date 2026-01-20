package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.Invoice;
import com.orbvpn.api.service.InvoiceService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InvoiceQueryResolver {
    private final InvoiceService invoiceService;

    @Secured({ ADMIN, RESELLER })
    @QueryMapping
    public List<Invoice> allInvoices() {
        log.info("Fetching all invoices");
        try {
            List<Invoice> invoices = invoiceService.getAll();
            log.info("Successfully retrieved {} invoices", invoices.size());
            return invoices;
        } catch (Exception e) {
            log.error("Error fetching invoices - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @QueryMapping
    public Invoice getInvoiceById(
            @Argument @Valid @Positive(message = "Invoice ID must be positive") Integer invoiceId) {
        log.info("Fetching invoice with id: {}", invoiceId);
        try {
            Invoice invoice = invoiceService.getById(invoiceId);
            if (invoice == null) {
                throw new NotFoundException("Invoice not found with id: " + invoiceId);
            }
            log.info("Successfully retrieved invoice: {}", invoiceId);
            return invoice;
        } catch (NotFoundException e) {
            log.warn("Invoice not found - ID: {} - Error: {}", invoiceId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching invoice: {} - Error: {}", invoiceId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @QueryMapping
    public Invoice getInvoiceByPaymentId(
            @Argument @Valid @Positive(message = "Payment ID must be positive") Integer paymentId) {
        log.info("Fetching invoice for payment id: {}", paymentId);
        try {
            Invoice invoice = invoiceService.getByPaymentId(paymentId);
            if (invoice == null) {
                throw new NotFoundException("Invoice not found for payment id: " + paymentId);
            }
            log.info("Successfully retrieved invoice for payment: {}", paymentId);
            return invoice;
        } catch (NotFoundException e) {
            log.warn("Invoice not found - Payment ID: {} - Error: {}", paymentId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching invoice for payment: {} - Error: {}", paymentId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @QueryMapping
    public List<Invoice> getInvoiceByDateRange(
            @Argument @Valid @NotBlank(message = "Begin date cannot be empty") String beginDate,
            @Argument @Valid @NotBlank(message = "End date cannot be empty") String endDate) {
        log.info("Fetching invoices between dates: {} and {}", beginDate, endDate);
        try {
            List<Invoice> invoices = invoiceService.getByDateRange(beginDate, endDate);
            log.info("Successfully retrieved {} invoices for date range", invoices.size());
            return invoices;
        } catch (Exception e) {
            log.error("Error fetching invoices for date range - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
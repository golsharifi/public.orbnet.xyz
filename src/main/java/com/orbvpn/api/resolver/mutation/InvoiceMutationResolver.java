package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.InvoicePDF;
import com.orbvpn.api.domain.dto.InvoiceUpdate;
import com.orbvpn.api.domain.entity.Invoice;
import com.orbvpn.api.service.InvoicePDFService;
import com.orbvpn.api.service.InvoiceService;
import lombok.RequiredArgsConstructor;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InvoiceMutationResolver {

    private final InvoiceService invoiceService;
    private final InvoicePDFService invoicePDFService;

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public Invoice updateInvoice(@Argument @Valid InvoiceUpdate invoiceUpdate) {
        log.info("Updating invoice: {}", invoiceUpdate.getId());
        try {
            return invoiceService.updateInvoice(invoiceUpdate);
        } catch (Exception e) {
            log.error("Error updating invoice - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public Boolean emailInvoicePDF(@Argument @Valid InvoicePDF invoicePdf) {
        log.info("Generating and emailing invoice PDF for invoice: {}", invoicePdf.getInvoiceId());
        try {
            String pdfFilename = invoicePDFService.createPDF(invoicePdf.getInvoiceId());
            if (pdfFilename == null) {
                return false;
            }
            invoicePDFService.emailPdf(pdfFilename, invoicePdf.getEmailsToSend());
            invoicePDFService.deleteAttachedFile(pdfFilename);
            return true;
        } catch (Exception e) {
            log.error("Error processing invoice PDF - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
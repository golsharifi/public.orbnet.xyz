package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.Invoice;
import com.orbvpn.api.utils.PDFUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvoicePDFService {

    public static final String INVOICES_FOLDER = "/var/tmp/invoices";

    private final InvoiceService invoiceService;
    private final PDFUtils pdfUtils;
    private final AsyncNotificationHelper asyncNotificationHelper;

    public String createPDF(int invoiceId) {
        Invoice invoice = invoiceService.getById(invoiceId);
        try {
            return pdfUtils.createPDF(invoice, INVOICES_FOLDER);
        } catch (Exception e) {
            log.error("Error creating PDF for the invoice {}.", invoiceId);
        }
        return null;
    }

    public void emailPdf(String filename, List<String> emails) {
        log.info("Emailing Invoice as PDF");

        Map<String, Object> variables = new HashMap<>();
        variables.put("attachedFile", filename);

        emails.forEach(email -> asyncNotificationHelper.sendInvoiceEmailAsync(
                email,
                variables,
                LocaleContextHolder.getLocale()));
    }

    public void deleteAttachedFile(String filename) {
        File attachedFile = new File(filename);
        if (attachedFile.delete()) {
            log.info("Deleted the file {}.", filename);
        } else {
            log.error("Error deleting the file {}.", filename);
        }
    }
}
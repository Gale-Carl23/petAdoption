package com.petdonation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class DonationPaymentSettings {

    private final String gcashRecipientName;
    private final String gcashRecipientNumber;
    private final String bankName;
    private final String bankAccountName;
    private final String bankAccountNumber;
    private final String qrPhImageUrl;

    public DonationPaymentSettings(
            @Value("${app.payment.gcash.recipient-name:}") String gcashRecipientName,
            @Value("${app.payment.gcash.recipient-number:}") String gcashRecipientNumber,
            @Value("${app.payment.bank.name:}") String bankName,
            @Value("${app.payment.bank.account-name:}") String bankAccountName,
            @Value("${app.payment.bank.account-number:}") String bankAccountNumber,
            @Value("${app.payment.qrph.image-url:}") String qrPhImageUrl) {
        this.gcashRecipientName = clean(gcashRecipientName);
        this.gcashRecipientNumber = clean(gcashRecipientNumber);
        this.bankName = clean(bankName);
        this.bankAccountName = clean(bankAccountName);
        this.bankAccountNumber = clean(bankAccountNumber);
        this.qrPhImageUrl = safeQrImageUrl(qrPhImageUrl);
    }

    public List<PaymentOption> options() {
        List<PaymentOption> options = new ArrayList<>();
        if (!gcashRecipientName.isBlank() && !gcashRecipientNumber.isBlank()) {
            options.add(new PaymentOption("GCASH", "GCash transfer", gcashRecipientName,
                    gcashRecipientNumber, "Send to this GCash account, then enter your transfer reference."));
        }
        if (!bankName.isBlank() && !bankAccountName.isBlank() && !bankAccountNumber.isBlank()) {
            options.add(new PaymentOption("BANK_TRANSFER", "Bank transfer", bankAccountName,
                    bankAccountNumber, bankName + " · enter your bank transfer reference."));
        }
        if (!qrPhImageUrl.isBlank()) {
            options.add(new PaymentOption("QR_PH", "QR Ph", "Pawprints of Hope",
                    qrPhImageUrl, "Scan the official QR Ph merchant code with a participating bank or e-wallet app."));
        }
        return List.copyOf(options);
    }

    public boolean isConfiguredMethod(String method) {
        return options().stream().anyMatch(option -> option.code().equals(method));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeQrImageUrl(String value) {
        String url = clean(value);
        if (url.startsWith("/images/") && !url.startsWith("//")) return url;
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? url : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public record PaymentOption(String code, String label, String recipientName,
                                String accountOrImage, String instructions) {
        public boolean qrCode() {
            return "QR_PH".equals(code);
        }
    }
}

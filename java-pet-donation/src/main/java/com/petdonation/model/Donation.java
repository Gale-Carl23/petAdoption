
package com.petdonation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "donations")
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_user_id", nullable = false)
    private User donor;

    @Column(length = 100)
    private String fullName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate dateSent;

    @Column(length = 500)
    private String proofFilePath;

    @Column(nullable = false, length = 30)
    private String paymentMethod = "OTHER";

    @Column(length = 100)
    private String paymentReference;

    @Column(name = "paymongo_session_id", length = 100, unique = true)
    private String paymongoSessionId;

    @Column(name = "paymongo_checkout_url", length = 1000)
    private String paymongoCheckoutUrl;

    @Column(name = "payment_idempotency_key", length = 36, unique = true)
    private String paymentIdempotencyKey;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false, length = 30)
    private String status = "PENDING_VERIFICATION";

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(length = 150)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    public Donation() {
    }

    public Long getId() {
        return id;
    }

    public User getDonor() {
        return donor;
    }

    public void setDonor(User donor) {
        this.donor = donor;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDateSent() {
        return dateSent;
    }

    public void setDateSent(LocalDate dateSent) {
        this.dateSent = dateSent;
    }

    public String getProofFilePath() {
        return proofFilePath;
    }

    public void setProofFilePath(String proofFilePath) {
        this.proofFilePath = proofFilePath;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getPaymongoSessionId() { return paymongoSessionId; }
    public void setPaymongoSessionId(String paymongoSessionId) { this.paymongoSessionId = paymongoSessionId; }
    public String getPaymongoCheckoutUrl() { return paymongoCheckoutUrl; }
    public void setPaymongoCheckoutUrl(String paymongoCheckoutUrl) { this.paymongoCheckoutUrl = paymongoCheckoutUrl; }
    public String getPaymentIdempotencyKey() { return paymentIdempotencyKey; }
    public void setPaymentIdempotencyKey(String paymentIdempotencyKey) { this.paymentIdempotencyKey = paymentIdempotencyKey; }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}

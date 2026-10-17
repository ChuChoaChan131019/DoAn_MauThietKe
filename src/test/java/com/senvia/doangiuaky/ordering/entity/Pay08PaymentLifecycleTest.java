
package com.senvia.doangiuaky.ordering.entity;

import com.senvia.doangiuaky.ordering.strategy.BankTransferStrategy;
import com.senvia.doangiuaky.ordering.strategy.CodPaymentStrategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class Pay08PaymentLifecycleTest {

    private static final BigDecimal AMOUNT =
            new BigDecimal("150000.00");

    private Payment createPayment(
            PaymentMethod method,
            PaymentStatus status
    ) {
        return new Payment(
                10L,
                method,
                AMOUNT,
                status,
                method == PaymentMethod.BANK_TRANSFER
                        ? "SENVIA-ORD-001"
                        : null
        );
    }

    @Test
    void supportsAllSixPaymentStatuses() {
        assertArrayEquals(
                new PaymentStatus[]{
                        PaymentStatus.PENDING,
                        PaymentStatus.COD_PENDING,
                        PaymentStatus.PAID,
                        PaymentStatus.FAILED,
                        PaymentStatus.CANCELLED,
                        PaymentStatus.REFUNDED
                },
                PaymentStatus.values()
        );
    }

    @Test
    void strategiesInitializeCorrectPaymentStatuses() {
        var codResult = new CodPaymentStrategy()
                .initialize("ORD-001", AMOUNT);

        var bankResult = new BankTransferStrategy()
                .initialize("ORD-002", AMOUNT);

        assertEquals(
                PaymentStatus.COD_PENDING,
                codResult.status()
        );
        assertEquals(
                PaymentStatus.PENDING,
                bankResult.status()
        );

        assertNull(codResult.transactionReference());
        assertNotNull(bankResult.transactionReference());
    }

    @Test
    void bankTransferPendingBecomesPaidWithPaidAt() {
        Payment payment = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        assertNull(payment.getPaidAt());
        assertNull(payment.getRefundedAt());

        payment.markBankTransferPaid();

        assertEquals(
                PaymentStatus.PAID,
                payment.getStatus()
        );
        assertNotNull(payment.getPaidAt());
        assertNull(payment.getRefundedAt());
    }

    @Test
    void bankTransferCannotBeConfirmedTwice() {
        Payment payment = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        payment.markBankTransferPaid();

        Instant firstPaidAt = payment.getPaidAt();

        assertThrows(
                IllegalStateException.class,
                payment::markBankTransferPaid
        );

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(firstPaidAt, payment.getPaidAt());
    }

    @Test
    void codPendingBecomesPaidWhenCollected() {
        Payment payment = createPayment(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING
        );

        payment.markCodPaid();

        assertEquals(
                PaymentStatus.PAID,
                payment.getStatus()
        );
        assertNotNull(payment.getPaidAt());
        assertNull(payment.getRefundedAt());
    }

    @Test
    void codCannotBeCollectedTwice() {
        Payment payment = createPayment(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING
        );

        payment.markCodPaid();
        Instant firstPaidAt = payment.getPaidAt();

        assertThrows(
                IllegalStateException.class,
                payment::markCodPaid
        );

        assertEquals(firstPaidAt, payment.getPaidAt());
    }

    @Test
    void unpaidBankTransferCancellationBecomesCancelled() {
        Payment payment = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        payment.settleForCancellation();

        assertEquals(
                PaymentStatus.CANCELLED,
                payment.getStatus()
        );
        assertNull(payment.getPaidAt());
        assertNull(payment.getRefundedAt());
    }

    @Test
    void unpaidCodCancellationBecomesCancelled() {
        Payment payment = createPayment(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING
        );

        payment.settleForCancellation();

        assertEquals(
                PaymentStatus.CANCELLED,
                payment.getStatus()
        );
        assertNull(payment.getPaidAt());
        assertNull(payment.getRefundedAt());
    }

    @Test
    void failedUnpaidPaymentCanBeCancelled() {
        Payment payment = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.FAILED
        );

        payment.settleForCancellation();

        assertEquals(
                PaymentStatus.CANCELLED,
                payment.getStatus()
        );
        assertNull(payment.getPaidAt());
        assertNull(payment.getRefundedAt());
    }

    @Test
    void paidBankTransferCancellationBecomesRefunded() {
        Payment payment = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        payment.markBankTransferPaid();

        Instant originalPaidAt = payment.getPaidAt();

        payment.settleForCancellation();

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );

        // Không xóa hoặc thay đổi thời gian nhận tiền.
        assertEquals(originalPaidAt, payment.getPaidAt());

        // Ghi thời gian hoàn tiền mô phỏng.
        assertNotNull(payment.getRefundedAt());

        assertFalse(
                payment.getRefundedAt()
                        .isBefore(originalPaidAt)
        );
    }

    @Test
    void cancelledPaymentCannotBeCancelledAgain() {
        Payment payment = createPayment(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING
        );

        payment.settleForCancellation();

        assertThrows(
                IllegalStateException.class,
                payment::settleForCancellation
        );

        assertEquals(
                PaymentStatus.CANCELLED,
                payment.getStatus()
        );
    }

    @Test
    void refundedPaymentCannotBeRefundedAgain() {
        Payment payment = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        payment.markBankTransferPaid();
        payment.settleForCancellation();

        Instant firstRefundedAt = payment.getRefundedAt();

        assertThrows(
                IllegalStateException.class,
                payment::settleForCancellation
        );

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );
        assertEquals(firstRefundedAt, payment.getRefundedAt());
    }

    @Test
    void cancelledPaymentCannotBecomePaid() {
        Payment bank = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        Payment cod = createPayment(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING
        );

        bank.settleForCancellation();
        cod.settleForCancellation();

        assertThrows(
                IllegalStateException.class,
                bank::markBankTransferPaid
        );

        assertThrows(
                IllegalStateException.class,
                cod::markCodPaid
        );

        assertEquals(PaymentStatus.CANCELLED, bank.getStatus());
        assertEquals(PaymentStatus.CANCELLED, cod.getStatus());
    }

    @Test
    void cannotPayUsingWrongPaymentMethod() {
        Payment cod = createPayment(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING
        );

        Payment bank = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        assertThrows(
                IllegalStateException.class,
                cod::markBankTransferPaid
        );

        assertThrows(
                IllegalStateException.class,
                bank::markCodPaid
        );

        assertEquals(
                PaymentStatus.COD_PENDING,
                cod.getStatus()
        );
        assertEquals(
                PaymentStatus.PENDING,
                bank.getStatus()
        );
    }

    @Test
    void paymentMarkedPaidWithoutPaidAtCannotBeRefunded() {
        Payment inconsistent = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PAID
        );

        // Dữ liệu có status PAID nhưng không có paid_at.
        assertNull(inconsistent.getPaidAt());

        assertThrows(
                IllegalStateException.class,
                inconsistent::settleForCancellation
        );

        assertEquals(
                PaymentStatus.PAID,
                inconsistent.getStatus()
        );
        assertNull(inconsistent.getRefundedAt());
    }

    @Test
    void invalidMethodStatusCombinationCannotBeCancelled() {
        Payment wrongCod = createPayment(
                PaymentMethod.COD,
                PaymentStatus.PENDING
        );

        Payment wrongBank = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.COD_PENDING
        );

        assertThrows(
                IllegalStateException.class,
                wrongCod::settleForCancellation
        );

        assertThrows(
                IllegalStateException.class,
                wrongBank::settleForCancellation
        );

        assertEquals(
                PaymentStatus.PENDING,
                wrongCod.getStatus()
        );
        assertEquals(
                PaymentStatus.COD_PENDING,
                wrongBank.getStatus()
        );
    }

    @Test
    void paymentAmountAndMethodRemainUnchangedAfterRefund() {
        Payment payment = createPayment(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING
        );

        payment.markBankTransferPaid();
        payment.settleForCancellation();

        assertEquals(
                PaymentMethod.BANK_TRANSFER,
                payment.getMethod()
        );

        assertEquals(
                0,
                AMOUNT.compareTo(payment.getAmount())
        );

        assertEquals(
                "SENVIA-ORD-001",
                payment.getTransactionReference()
        );
    }
}
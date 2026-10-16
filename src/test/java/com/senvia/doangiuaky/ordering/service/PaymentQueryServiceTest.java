
package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.entity.Payment;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentQueryServiceTest {

    private final PaymentRepository repository =
            mock(PaymentRepository.class);

    private final PaymentQueryService service =
            new PaymentQueryService(repository);

    @Test
    void readsPaymentMethodAndStatusFromPaymentsOnly() {

        Payment payment = new Payment(
                42L,
                PaymentMethod.BANK_TRANSFER,
                new BigDecimal("200000.00"),
                PaymentStatus.PAID,
                "BANK-42"
        );

        when(repository.findByOrderId(42L))
                .thenReturn(Optional.of(payment));

        PaymentDetails result = service.getByOrderId(42L);

        assertEquals(42L, result.orderId());
        assertEquals(PaymentMethod.BANK_TRANSFER, result.method());
        assertEquals(PaymentStatus.PAID, result.status());
        assertEquals(new BigDecimal("200000.00"), result.amount());
        assertEquals("BANK-42", result.transactionReference());

        verify(repository).findByOrderId(42L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void readsCurrentStatusOnEachRequestInsteadOfCachingOldStatus() {

        Payment pending = new Payment(
                24L,
                PaymentMethod.BANK_TRANSFER,
                new BigDecimal("100.00"),
                PaymentStatus.PENDING,
                "REF-24"
        );

        Payment paid = new Payment(
                24L,
                PaymentMethod.BANK_TRANSFER,
                new BigDecimal("100.00"),
                PaymentStatus.PAID,
                "REF-24"
        );

        when(repository.findByOrderId(24L))
                .thenReturn(
                        Optional.of(pending),
                        Optional.of(paid)
                );

        assertEquals(
                PaymentStatus.PENDING,
                service.getByOrderId(24L).status()
        );

        assertEquals(
                PaymentStatus.PAID,
                service.getByOrderId(24L).status()
        );

        verify(repository, times(2)).findByOrderId(24L);
    }

    @Test
    void missingPaymentIsNotReplacedByAnAssumedOrderStatus() {

        when(repository.findByOrderId(404L))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> service.getByOrderId(404L)
        );
    }

    @Test
    void invalidOrderIdDoesNotAccessDatabase() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getByOrderId(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getByOrderId(0L)
        );

        verifyNoInteractions(repository);
    }
}


package com.senvia.doangiuaky.ordering.strategy;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.service.OrderDraftWriter;
import com.senvia.doangiuaky.ordering.service.PaymentInitializationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStrategyTest {

    private PaymentStrategyFactory factory;
    private PaymentInitializationService paymentService;

    @BeforeEach
    void setUp() {
        factory = new PaymentStrategyFactory(
                List.of(
                        new CodPaymentStrategy(),
                        new BankTransferStrategy()
                )
        );

        paymentService = new PaymentInitializationService(factory);
    }

    @Test
    void codShouldCreateCodPendingPayment() {
        BigDecimal amount = new BigDecimal("250000.00");

        PaymentResult result = factory
                .getStrategy(PaymentMethod.COD)
                .initialize("ORD-001", amount);

        assertEquals(PaymentMethod.COD, result.method());
        assertEquals(PaymentStatus.COD_PENDING, result.status());
        assertEquals(0, amount.compareTo(result.amount()));
        assertNull(result.transactionReference());
        assertFalse(result.instruction().isBlank());
    }

    @Test
    void bankTransferShouldCreatePendingPayment() {
        BigDecimal amount = new BigDecimal("350000.00");

        PaymentResult result = factory
                .getStrategy(PaymentMethod.BANK_TRANSFER)
                .initialize("ORD-002", amount);

        assertEquals(PaymentMethod.BANK_TRANSFER, result.method());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertEquals(0, amount.compareTo(result.amount()));
        assertEquals(
                "SENVIA-ORD-002",
                result.transactionReference()
        );
        assertTrue(
                result.instruction()
                        .contains(result.transactionReference())
        );
    }

    @Test
    void differentOrdersShouldHaveDifferentReferences() {
        PaymentStrategy strategy =
                factory.getStrategy(PaymentMethod.BANK_TRANSFER);

        BigDecimal amount = new BigDecimal("100000.00");

        PaymentResult first =
                strategy.initialize("ORD-001", amount);

        PaymentResult second =
                strategy.initialize("ORD-002", amount);

        assertNotEquals(
                first.transactionReference(),
                second.transactionReference()
        );
    }

    @Test
    void sameOrderShouldKeepSameReference() {
        PaymentStrategy strategy =
                factory.getStrategy(PaymentMethod.BANK_TRANSFER);

        BigDecimal amount = new BigDecimal("120000.00");

        PaymentResult first =
                strategy.initialize("ORD-ABC", amount);

        PaymentResult second =
                strategy.initialize("ORD-ABC", amount);

        assertEquals(
                first.transactionReference(),
                second.transactionReference()
        );
    }

    @Test
    void factoryShouldChooseCorrectStrategies() {
        assertInstanceOf(
                CodPaymentStrategy.class,
                factory.getStrategy(PaymentMethod.COD)
        );

        assertInstanceOf(
                BankTransferStrategy.class,
                factory.getStrategy(PaymentMethod.BANK_TRANSFER)
        );
    }

    @Test
    void factoryShouldRejectInvalidStrategies() {
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.getStrategy(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentStrategyFactory(
                        List.of(new CodPaymentStrategy())
                ).getStrategy(PaymentMethod.BANK_TRANSFER)
        );

        assertThrows(
                IllegalStateException.class,
                () -> new PaymentStrategyFactory(
                        List.of(
                                new CodPaymentStrategy(),
                                new CodPaymentStrategy()
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidAmounts() {
        PaymentStrategy cod =
                factory.getStrategy(PaymentMethod.COD);

        assertThrows(
                IllegalArgumentException.class,
                () -> cod.initialize(
                        "ORD-001", BigDecimal.ZERO
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cod.initialize(
                        "ORD-001", new BigDecimal("-10.00")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cod.initialize(
                        "ORD-001", new BigDecimal("100.001")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cod.initialize(
                        "ORD-001",
                        new BigDecimal("1000000000000.00")
                )
        );
    }

    @Test
    void shouldRejectInvalidOrderCode() {
        PaymentStrategy bank =
                factory.getStrategy(PaymentMethod.BANK_TRANSFER);

        assertThrows(
                IllegalArgumentException.class,
                () -> bank.initialize(
                        "", new BigDecimal("10000.00")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> bank.initialize(
                        "INVALID CODE!",
                        new BigDecimal("10000.00")
                )
        );
    }

    @Test
    void serviceShouldConvertResultToPaymentSeed() {
        BigDecimal amount = new BigDecimal("450000.00");

        OrderDraftWriter.PaymentSeed cod =
                paymentService.initialize(
                        PaymentMethod.COD,
                        "ORD-001",
                        amount
                );

        assertEquals(
                PaymentStatus.COD_PENDING,
                cod.status()
        );
        assertNull(cod.transactionReference());

        OrderDraftWriter.PaymentSeed bank =
                paymentService.initialize(
                        PaymentMethod.BANK_TRANSFER,
                        "ORD-002",
                        amount
                );

        assertEquals(PaymentStatus.PENDING, bank.status());
        assertEquals(
                "SENVIA-ORD-002",
                bank.transactionReference()
        );
    }

    @Test
    void serviceShouldRejectStrategyReturningDifferentMethod() {
        BigDecimal amount = new BigDecimal("450000.00");
        PaymentResult wrongMethod = new PaymentResult(
                PaymentMethod.BANK_TRANSFER, amount, PaymentStatus.PENDING,
                "SENVIA-ORD-001", "Chuyển khoản mô phỏng."
        );

        PaymentInitializationService service = serviceWithCodResult(wrongMethod);

        assertThrows(IllegalStateException.class,
                () -> service.initialize(PaymentMethod.COD, "ORD-001", amount));
    }

    @Test
    void serviceShouldRejectStrategyReturningDifferentAmount() {
        BigDecimal amount = new BigDecimal("450000.00");
        PaymentResult wrongAmount = new PaymentResult(
                PaymentMethod.COD, new BigDecimal("450001.00"), PaymentStatus.COD_PENDING,
                null, "Thanh toán khi nhận hàng."
        );

        PaymentInitializationService service = serviceWithCodResult(wrongAmount);

        assertThrows(IllegalStateException.class,
                () -> service.initialize(PaymentMethod.COD, "ORD-001", amount));
    }

    private static PaymentInitializationService serviceWithCodResult(PaymentResult result) {
        PaymentStrategy strategy = new PaymentStrategy() {
            @Override
            public PaymentMethod supportedMethod() {
                return PaymentMethod.COD;
            }

            @Override
            public PaymentResult initialize(String orderCode, BigDecimal amount) {
                return result;
            }
        };

        return new PaymentInitializationService(new PaymentStrategyFactory(List.of(strategy)));
    }
}

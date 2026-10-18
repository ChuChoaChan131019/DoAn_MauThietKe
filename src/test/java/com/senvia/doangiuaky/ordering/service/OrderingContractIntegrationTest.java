package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.api.OrderHistoryApi;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderItem;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.Payment;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class OrderingContractIntegrationTest {
    @Autowired OrderingContractService service;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean CurrentUserApi currentUser;
    @Autowired MerchantApi merchant;
    @MockitoBean OrderHistoryApi histories;

    private Long buyerId;
    private Long otherBuyerId;
    private Long shopId;
    private Long otherShopId;
    private Long ownerId;

    @BeforeEach
    void loadSeedIds() {
        List<Long> buyers = jdbc.queryForList(
                "SELECT id FROM users WHERE role = 'USER' ORDER BY id LIMIT 2", Long.class);
        List<Long> shops = jdbc.queryForList(
                "SELECT id FROM shops ORDER BY id LIMIT 2", Long.class);
        assertEquals(2, buyers.size(), "Seed cần hai USER để kiểm tra ownership");
        assertEquals(2, shops.size(), "Seed cần hai shop để kiểm tra phạm vi thống kê");
        buyerId = buyers.get(0);
        otherBuyerId = buyers.get(1);
        shopId = shops.get(0);
        otherShopId = shops.get(1);
        ownerId = jdbc.queryForObject("SELECT owner_id FROM shops WHERE id = ?", Long.class, shopId);
        actAs(buyerId, UserRole.USER);
    }

    @Test
    void buyerFilterAndDetailReadOnlyOwnRowsFromDatabase() {
        Order ownCompleted = saveOrder(buyerId, shopId, OrderStatus.COMPLETED, "20.00");
        saveOrder(buyerId, shopId, OrderStatus.PENDING, "30.00");
        Order foreign = saveOrder(otherBuyerId, shopId, OrderStatus.COMPLETED, "40.00");
        payments.saveAndFlush(new Payment(ownCompleted.getId(), PaymentMethod.COD,
                new BigDecimal("20.00"), PaymentStatus.PAID, null));
        when(histories.forCurrentBuyer(ownCompleted.getOrderCode())).thenReturn(List.of());

        var page = service.listOwnOrders("completed", 1, 10);
        assertEquals(1, page.totalElements());
        assertEquals(ownCompleted.getOrderCode(), page.orders().getFirst().orderCode());
        assertEquals(2, service.listOwnOrders(null, 1, 1).totalElements());
        assertEquals(1, service.listOwnOrders(null, 2, 1).orders().size());
        assertEquals("PAID", service.getOwnOrder(ownCompleted.getOrderCode()).payment().status());
        assertStatus(404, () -> service.getOwnOrder(foreign.getOrderCode()));
    }

    @Test
    void adminFiltersDatabaseAndRejectsBuyerRole() {
        Order expected = saveOrder(buyerId, shopId, OrderStatus.COMPLETED, "20.00");
        saveOrder(buyerId, otherShopId, OrderStatus.COMPLETED, "30.00");
        saveOrder(otherBuyerId, shopId, OrderStatus.COMPLETED, "40.00");
        assertStatus(403, () -> service.listOrders(null, null, null, null, 1, 10));

        actAs(999L, UserRole.ADMIN);
        var page = service.listOrders("COMPLETED", buyerId, shopId,
                expected.getOrderCode(), 1, 1);
        assertEquals(1, page.totalElements());
        assertEquals(expected.getOrderCode(), page.orders().getFirst().orderCode());
        assertEquals(0, service.listOrders(null, null, null, "%", 1, 10).totalElements());
        assertStatus(400, () -> service.listOrders("UNKNOWN", null, null, null, 1, 10));
        assertStatus(400, () -> service.listOrders(null, null, null, null, 0, 10));
    }

    @Test
    void reviewVerificationChecksRealBuyerProductAndCompletedStatus() {
        Long productId = jdbc.queryForObject(
                "SELECT id FROM products WHERE shop_id = ? ORDER BY id LIMIT 1", Long.class, shopId);
        assertNotNull(productId);
        Order completed = saveOrder(buyerId, shopId, OrderStatus.COMPLETED, "20.00");
        Order pending = saveOrder(buyerId, shopId, OrderStatus.PENDING, "20.00");
        Order foreign = saveOrder(otherBuyerId, shopId, OrderStatus.COMPLETED, "20.00");
        Long completedItem = saveItem(completed, productId);
        Long pendingItem = saveItem(pending, productId);
        Long foreignItem = saveItem(foreign, productId);

        assertEquals(completedItem, service.requireReviewableItem(completedItem, productId).orderItemId());
        assertStatus(404, () -> service.requireReviewableItem(completedItem, productId + 1));
        assertStatus(404, () -> service.requireReviewableItem(foreignItem, productId));
        assertStatus(409, () -> service.requireReviewableItem(pendingItem, productId));
        actAs(999L, UserRole.ADMIN);
        assertStatus(403, () -> service.requireReviewableItem(completedItem, productId));
    }

    @Test
    void statisticsCountAndRevenueUseOnlyOwnShopAndCompletedOrders() {
        actAs(ownerId, UserRole.USER);
        assertTrue(merchant.findShopSummary(shopId).isPresent());
        var before = service.forOwnShop(shopId);

        saveOrder(buyerId, shopId, OrderStatus.PENDING, "10.00");
        saveOrder(buyerId, shopId, OrderStatus.COMPLETED, "25.00");
        saveOrder(otherBuyerId, otherShopId, OrderStatus.COMPLETED, "99.00");

        var after = service.forOwnShop(shopId);
        assertEquals(before.totalOrders() + 2, after.totalOrders());
        assertEquals(before.ordersByStatus().get("PENDING") + 1,
                after.ordersByStatus().get("PENDING"));
        assertEquals(before.ordersByStatus().get("COMPLETED") + 1,
                after.ordersByStatus().get("COMPLETED"));
        assertEquals(0, before.completedRevenue().add(new BigDecimal("25.00"))
                .compareTo(after.completedRevenue()));

        actAs(otherBuyerId, UserRole.USER);
        assertStatus(404, () -> service.forOwnShop(shopId));
        actAs(999L, UserRole.ADMIN);
        assertStatus(403, () -> service.forOwnShop(shopId));
    }

    private Order saveOrder(Long buyer, Long shop, OrderStatus status, String amount) {
        Order order = new Order("C90-" + UUID.randomUUID().toString().substring(0, 12),
                buyer, shop, "Người nhận", "0900000000", "Địa chỉ thử nghiệm", null,
                new BigDecimal(amount));
        order.changeStatus(status);
        return orders.saveAndFlush(order);
    }

    private Long saveItem(Order order, Long productId) {
        return items.saveAndFlush(new OrderItem(order.getId(), productId, "Sản phẩm thử nghiệm",
                new BigDecimal("20.00"), 1, new BigDecimal("20.00"))).getId();
    }

    private void actAs(Long userId, UserRole role) {
        when(currentUser.requireCurrentUser()).thenReturn(
                new UserSummary(userId, "Người thử nghiệm", role, AccountStatus.ACTIVE));
    }

    private static void assertStatus(int status, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, error.getStatusCode().value());
    }
}

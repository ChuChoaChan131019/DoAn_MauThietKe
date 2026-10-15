package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.merchant.api.MerchantShopRequestApi;
import com.senvia.doangiuaky.merchant.api.PendingShopRequestCard;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private final MerchantShopRequestApi merchantShopRequestApi = mock(MerchantShopRequestApi.class);
    private final AdminController controller = new AdminController(merchantShopRequestApi);

    @Test
    void dashboardUsesPendingShopRequestsFromMerchantApi() {
        PendingShopRequestCard request = new PendingShopRequestCard(
                42L,
                "Real shop",
                "Nguyễn Văn A",
                Instant.parse("2026-10-08T10:00:00Z"));
        List<PendingShopRequestCard> requests = List.of(request);
        when(merchantShopRequestApi.findPendingShopRequests()).thenReturn(requests);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("engagement/admin-dashboard", controller.adminDashboard(model));
        assertEquals(1, model.getAttribute("pendingCount"));
        assertSame(requests, model.getAttribute("pendingShopRequests"));
        verify(merchantShopRequestApi).findPendingShopRequests();
    }

    @Test
    void dashboardShowsZeroWhenThereAreNoPendingShopRequests() {
        when(merchantShopRequestApi.findPendingShopRequests()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.adminDashboard(model);

        assertEquals(0, model.getAttribute("pendingCount"));
        assertEquals(List.of(), model.getAttribute("pendingShopRequests"));
    }

    @Test
    void legacyAdminAccountsRouteRedirectsToIdentityManagementPage() {
        assertEquals("redirect:/identity/admin/accounts", controller.adminAccounts());
    }
}

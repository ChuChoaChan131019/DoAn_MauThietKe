package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CheckoutPreviewService {
    private final CheckoutPreparationService preparationService;

    public CheckoutPreviewService(CheckoutPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @Transactional
    public PreparedCheckout preview(CheckoutPaymentForm form, List<Long> selectedItemIds) {
        return preparationService.prepare(form, selectedItemIds);
    }
}

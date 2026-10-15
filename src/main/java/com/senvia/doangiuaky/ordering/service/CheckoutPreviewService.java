package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutPreviewService {
    private final CheckoutPreparationService preparationService;

    public CheckoutPreviewService(CheckoutPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @Transactional
    public PreparedCheckout preview(CheckoutPaymentForm form) {
        return preparationService.prepare(form);
    }
}

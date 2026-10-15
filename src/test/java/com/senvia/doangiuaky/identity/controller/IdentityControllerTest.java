package com.senvia.doangiuaky.identity.controller;

import com.senvia.doangiuaky.identity.dto.RegistrationForm;
import com.senvia.doangiuaky.identity.service.IdentityService;
import com.senvia.doangiuaky.identity.service.InvalidPasswordException;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class IdentityControllerTest {

    @Test
    void registrationReturnsPasswordLimitErrorToForm() {
        IdentityService identityService = mock(IdentityService.class);
        doThrow(new InvalidPasswordException("Mật khẩu không được vượt quá 72 byte."))
                .when(identityService).register(any(RegistrationForm.class));
        IdentityController controller = new IdentityController(identityService);
        RegistrationForm form = new RegistrationForm();
        form.setFullName("Nguyen Van A");
        form.setEmail("buyer@example.com");
        form.setPassword("é".repeat(37));
        form.setConfirmPassword(form.getPassword());
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "registrationForm");

        String view = controller.register(form, bindingResult);

        assertEquals("identity/register", view);
        assertEquals("password", bindingResult.getFieldError().getField());
        assertEquals("Mật khẩu không được vượt quá 72 byte.", bindingResult.getFieldError().getDefaultMessage());
    }
}

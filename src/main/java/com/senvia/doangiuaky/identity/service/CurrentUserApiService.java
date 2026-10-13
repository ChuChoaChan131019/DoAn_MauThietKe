package com.senvia.doangiuaky.identity.service;

import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.identity.entity.User;
import com.senvia.doangiuaky.identity.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CurrentUserApiService implements CurrentUserApi {

    private final UserRepository userRepository;

    public CurrentUserApiService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserSummary requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Bạn cần đăng nhập để tiếp tục.");
        }

        User user = userRepository.findByEmailIgnoreCase(User.normalizeEmail(authentication.getName()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Không xác định được tài khoản hiện tại."));
        return new UserSummary(user.getId(), user.getFullName(), user.getRole(), user.getAccountStatus());
    }
}

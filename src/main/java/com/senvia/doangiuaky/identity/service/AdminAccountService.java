package com.senvia.doangiuaky.identity.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.dto.AdminAccountView;
import com.senvia.doangiuaky.identity.entity.User;
import com.senvia.doangiuaky.identity.repository.UserRepository;
import com.senvia.doangiuaky.identity.security.SessionInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class AdminAccountService {

    private final UserRepository userRepository;
    private final SessionInvalidator sessionInvalidator;

    public AdminAccountService(UserRepository userRepository, SessionInvalidator sessionInvalidator) {
        this.userRepository = userRepository;
        this.sessionInvalidator = sessionInvalidator;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountView> search(String query, AccountStatus status) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        List<User> accounts;
        if (normalizedQuery == null && status == null) {
            accounts = userRepository.findAllByOrderByCreatedAtDesc();
        } else if (normalizedQuery == null) {
            accounts = userRepository.findAllByAccountStatusOrderByCreatedAtDesc(status);
        } else if (status == null) {
            accounts = userRepository
                    .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(
                            normalizedQuery, normalizedQuery);
        } else {
            accounts = userRepository
                    .findByAccountStatusAndFullNameContainingIgnoreCaseOrAccountStatusAndEmailContainingIgnoreCaseOrderByCreatedAtDesc(
                            status, normalizedQuery, status, normalizedQuery);
        }
        return accounts.stream()
                .map(AdminAccountService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminAccountView getAccount(Long userId) {
        return toView(findEntity(userId));
    }

    @Transactional
    public void lock(Long targetId, Long actorId, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(normalizedReason) || normalizedReason.length() > 500) {
            throw new AccountOperationException("Lý do khóa phải từ 1 đến 500 ký tự.");
        }

        User actor = findActiveAdmin(actorId);
        User target = findEntity(targetId);
        if (target.getId().equals(actorId)) {
            throw new AccountOperationException("Bạn không thể tự khóa tài khoản.");
        }
        if (target.getRole() != UserRole.USER) {
            throw new AccountOperationException("Chỉ được khóa tài khoản USER.");
        }
        if (target.getAccountStatus() == AccountStatus.LOCKED) {
            throw new AccountOperationException("Tài khoản đã bị khóa.");
        }

        target.setAccountStatus(AccountStatus.LOCKED);
        target.setLockReason(normalizedReason);
        target.setLockedBy(actor);
        target.setLockedAt(Instant.now());
        userRepository.saveAndFlush(target);
        Long lockedUserId = target.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionInvalidator.invalidate(lockedUserId);
            }
        });
    }

    @Transactional
    public void unlock(Long targetId, Long actorId) {
        findActiveAdmin(actorId);
        User target = findEntity(targetId);
        if (target.getRole() != UserRole.USER) {
            throw new AccountOperationException("Chỉ được mở khóa tài khoản USER.");
        }
        if (target.getAccountStatus() != AccountStatus.LOCKED) {
            throw new AccountOperationException("Tài khoản không ở trạng thái bị khóa.");
        }

        target.setAccountStatus(AccountStatus.ACTIVE);
        target.setLockReason(null);
        target.setLockedBy(null);
        target.setLockedAt(null);
        userRepository.save(target);
    }

    private User findEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AccountOperationException("Tài khoản không tồn tại."));
    }

    private User findActiveAdmin(Long userId) {
        User actor = findEntity(userId);
        if (actor.getRole() != UserRole.ADMIN || actor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountOperationException("Chỉ quản trị viên đang hoạt động mới được thực hiện thao tác này.");
        }
        return actor;
    }

    private static AdminAccountView toView(User user) {
        String lockedByName = user.getLockedBy() == null ? null : user.getLockedBy().getFullName();
        return new AdminAccountView(
                user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getAccountStatus(), user.getLockReason(), lockedByName, user.getLockedAt());
    }
}

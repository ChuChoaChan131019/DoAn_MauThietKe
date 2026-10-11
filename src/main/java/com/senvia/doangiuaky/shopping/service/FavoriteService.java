package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.shopping.dto.FavoriteItemView;
import com.senvia.doangiuaky.shopping.entity.Favorite;
import com.senvia.doangiuaky.shopping.repository.FavoriteRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final IdentityApi identityApi;
    private final Supplier<MerchantApi> merchantApiSupplier;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public FavoriteService(
            FavoriteRepository favoriteRepository,
            IdentityApi identityApi,
            ObjectProvider<MerchantApi> merchantApiProvider,
            PlatformTransactionManager transactionManager) {
        this(favoriteRepository, identityApi,
                () -> merchantApiProvider.getIfAvailable(() -> productId -> java.util.Optional.empty()),
                new TransactionTemplate(transactionManager));
    }

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            IdentityApi identityApi,
            MerchantApi merchantApi) {
        this(favoriteRepository, identityApi, () -> merchantApi, null);
    }

    private FavoriteService(
            FavoriteRepository favoriteRepository,
            IdentityApi identityApi,
            Supplier<MerchantApi> merchantApiSupplier,
            TransactionTemplate transactionTemplate) {
        this.favoriteRepository = favoriteRepository;
        this.identityApi = identityApi;
        this.merchantApiSupplier = merchantApiSupplier;
        this.transactionTemplate = transactionTemplate;
    }

    public void addFavorite(Long currentUserId, Long productId) {
        validatePositiveId(productId, "Sản phẩm không hợp lệ.");
        validateCurrentUser(currentUserId);
        merchantApiSupplier.get().findProductForCart(productId)
                .orElseThrow(() -> new FavoriteOperationException("Sản phẩm không tồn tại."));

        if (transactionTemplate == null) {
            addFavoriteInTransaction(currentUserId, productId);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status ->
                    addFavoriteInTransaction(currentUserId, productId));
        } catch (DataIntegrityViolationException | UnexpectedRollbackException exception) {
            if (isFavoriteUniqueConflict(exception)) {
                return;
            }
            if (isFavoriteForeignKeyConflict(exception)) {
                throw new FavoriteOperationException("Sản phẩm hoặc tài khoản không còn tồn tại.");
            }
            throw exception;
        }
    }

    private void addFavoriteInTransaction(Long currentUserId, Long productId) {
        if (favoriteRepository.existsByUserIdAndProductId(currentUserId, productId)) {
            return;
        }
        favoriteRepository.saveAndFlush(new Favorite(currentUserId, productId));
    }

    public void removeFavorite(Long currentUserId, Long productId) {
        validatePositiveId(productId, "Sản phẩm không hợp lệ.");
        validateCurrentUser(currentUserId);

        if (transactionTemplate == null) {
            favoriteRepository.deleteByUserIdAndProductId(currentUserId, productId);
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                favoriteRepository.deleteByUserIdAndProductId(currentUserId, productId));
    }

    @Transactional(readOnly = true)
    public List<FavoriteItemView> listForUser(Long currentUserId) {
        validateCurrentUser(currentUserId);
        return favoriteRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(currentUserId).stream()
                .map(favorite -> merchantApiSupplier.get().findProductForCart(favorite.getProductId())
                        .map(product -> FavoriteItemView.from(favorite, product))
                        .orElseGet(() -> FavoriteItemView.unavailable(favorite)))
                .toList();
    }

    private void validateCurrentUser(Long currentUserId) {
        validatePositiveId(currentUserId, "Không xác định được người dùng.");
        UserSummary user = identityApi.findUser(currentUserId)
                .orElseThrow(() -> new FavoriteOperationException("Tài khoản không tồn tại."));
        if (user.role() != UserRole.USER) {
            throw new FavoriteOperationException("Tài khoản này không được phép sử dụng danh sách yêu thích.");
        }
        if (user.accountStatus() != AccountStatus.ACTIVE) {
            throw new FavoriteOperationException("Tài khoản đang bị khóa.");
        }
    }

    private static void validatePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new FavoriteOperationException(message);
        }
    }

    private static boolean isFavoriteUniqueConflict(Throwable exception) {
        return hasConstraint(exception, "uk_favorites_user_product", "favorites", "unique", "duplicate");
    }

    private static boolean isFavoriteForeignKeyConflict(Throwable exception) {
        return hasConstraint(exception, "fk_favorites_product", "favorites", "foreign key", "referential")
                || hasConstraint(exception, "fk_favorites_user", "favorites", "foreign key", "referential");
    }

    private static boolean hasConstraint(
            Throwable exception,
            String constraintName,
            String tableName,
            String... markers) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String actualName = violation.getConstraintName();
                if (actualName != null && constraintName.equalsIgnoreCase(actualName)) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains(constraintName.toLowerCase(Locale.ROOT))) {
                    return true;
                }
                boolean containsMarker = false;
                for (String marker : markers) {
                    containsMarker |= normalized.contains(marker);
                }
                if (normalized.contains(tableName) && containsMarker) {
                    return true;
                }
            }
        }
        return false;
    }
}

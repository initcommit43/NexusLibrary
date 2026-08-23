package dev.nexus.core.importing;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ExternalAccountRepository;
import dev.nexus.core.domain.Provider;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns a user's links to external services. Scoped by {@code userId} in every query, so one
 * user can never read, re-link or disconnect another user's account.
 */
@Service
public class ExternalAccountService {

    private final ExternalAccountRepository accounts;

    public ExternalAccountService(ExternalAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<ExternalAccount> listFor(Long userId) {
        return accounts.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public ExternalAccount requireConnected(Long userId, Provider provider) {
        return accounts.findByUserIdAndProvider(userId, provider)
                .orElseThrow(ExternalAccountNotConnectedException::new);
    }

    /** Connecting again with a different account re-points the existing link. */
    @Transactional
    public ExternalAccount connect(Long userId, Provider provider, String externalUserId) {
        return connect(userId, provider, externalUserId, null, null, null);
    }

    /**
     * The OAuth variant. Tokens are encrypted by the entity's converter on the way to the
     * column, so nothing here has to know they are sensitive.
     */
    @Transactional
    public ExternalAccount connect(
            Long userId,
            Provider provider,
            String externalUserId,
            String accessToken,
            String refreshToken,
            Instant expiresAt) {

        ExternalAccount account = accounts.findByUserIdAndProvider(userId, provider)
                .orElseGet(() -> new ExternalAccount(userId, provider, externalUserId));

        account.setExternalUserId(externalUserId);
        if (accessToken != null) {
            account.setAccessToken(accessToken);
            account.setRefreshToken(refreshToken);
            account.setTokenExpiresAt(expiresAt);
        }
        return accounts.save(account);
    }

    @Transactional
    public void disconnect(Long userId, Provider provider) {
        if (accounts.deleteByUserIdAndProvider(userId, provider) == 0) {
            throw new ExternalAccountNotConnectedException();
        }
    }
}

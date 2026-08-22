package dev.nexus.core.importing;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ExternalAccountRepository;
import dev.nexus.core.domain.Provider;
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
        return accounts.findByUserIdAndProvider(userId, provider)
                .map(existing -> {
                    existing.setExternalUserId(externalUserId);
                    return accounts.save(existing);
                })
                .orElseGet(() -> accounts.save(new ExternalAccount(userId, provider, externalUserId)));
    }

    @Transactional
    public void disconnect(Long userId, Provider provider) {
        if (accounts.deleteByUserIdAndProvider(userId, provider) == 0) {
            throw new ExternalAccountNotConnectedException();
        }
    }
}

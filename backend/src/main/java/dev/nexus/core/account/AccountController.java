package dev.nexus.core.account;

import dev.nexus.auth.AppUser;
import dev.nexus.auth.CurrentUser;
import dev.nexus.auth.RefreshTokenService;
import dev.nexus.auth.SessionResponses;
import dev.nexus.auth.dto.AuthResponse;
import dev.nexus.auth.dto.UserResponse;
import dev.nexus.core.account.AccountRequests.AccountDeletion;
import dev.nexus.core.account.AccountRequests.PasswordChange;
import dev.nexus.core.account.AccountRequests.ProfileUpdate;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader's own account.
 *
 * <p>Every route reads the reader from the token and takes no id, so there is no id to
 * tamper with: nobody can rename, read out or delete an account other than their own.
 */
@RestController
@RequestMapping("/settings/account")
public class AccountController {

    private final AccountService accounts;
    private final RefreshTokenService refreshTokens;
    private final SessionResponses sessions;

    public AccountController(
            AccountService accounts, RefreshTokenService refreshTokens, SessionResponses sessions) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
    }

    @PatchMapping
    public UserResponse updateProfile(
            @AuthenticationPrincipal CurrentUser user, @RequestBody @Valid ProfileUpdate update) {

        return UserResponse.from(accounts.updateProfile(user.id(), update));
    }

    /**
     * Changes the password and ends every session the old one could have left behind — on a
     * shared machine, an old phone, a browser signed in and forgotten. Withdrawing the other
     * sessions is most of the reason anyone changes a password under duress, and until there
     * was a table of the tokens still allowed it was not something this could do.
     *
     * <p>The caller is then given a new session rather than being signed out with everyone
     * else. Their access token still has minutes left on it, so cutting only the refresh
     * token would leave them working normally and then dropped, with the password change the
     * last thing they would think to blame.
     */
    @PostMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(
            @AuthenticationPrincipal CurrentUser user, @RequestBody @Valid PasswordChange change) {

        AppUser changed = accounts.changePassword(user.id(), change);
        refreshTokens.endEverySession(changed.getId());

        return sessions.issue(refreshTokens.begin(changed, change.client()), HttpStatus.OK);
    }

    /**
     * Everything held about this reader, as a file.
     *
     * <p>Sent as an attachment rather than a page of JSON: this exists to be kept, and the
     * date in the name is what tells two of them apart a year later.
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal CurrentUser user) {
        Map<String, Object> data = accounts.export(user.id());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nexus-data-" + java.time.LocalDate.now() + ".json\"")
                .body(data);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CurrentUser user, @RequestBody @Valid AccountDeletion request) {

        accounts.delete(user.id(), request.password());
        return ResponseEntity.noContent().build();
    }
}

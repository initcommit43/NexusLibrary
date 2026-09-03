package dev.nexus.core.account;

import dev.nexus.auth.AuthClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the account section can ask for. The rules match registration's, deliberately. */
public final class AccountRequests {

    private AccountRequests() {}

    /** Either field may be left out; what is sent is what changes. */
    public record ProfileUpdate(
            @Email @Size(max = 320) String email,
            @Size(min = 3, max = 32)
                    @Pattern(
                            regexp = "^[a-zA-Z0-9_-]+$",
                            message = "may only contain letters, numbers, underscores and hyphens")
                    String username) {}

    /**
     * The current password is asked for even though the caller is already signed in: a token
     * left behind on a shared machine should not be enough to take the account with it.
     */
    public record PasswordChange(
            @NotBlank String currentPassword,
            // bcrypt silently ignores input past 72 bytes, so cap it rather than let a
            // longer password give a false sense of strength.
            @NotBlank @Size(min = 12, max = 72) String newPassword,
            // Changing a password ends every session and starts one more, so the caller has
            // to say where the replacement goes. Required, as it is on login: guessing browser
            // here would hand a native client a session it cannot keep.
            @NotNull AuthClient client) {}

    /** Deleting everything is worth one more proof that it is the account's owner asking. */
    public record AccountDeletion(@NotBlank String password) {}
}

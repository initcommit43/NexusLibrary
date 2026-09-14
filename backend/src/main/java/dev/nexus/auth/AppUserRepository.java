package dev.nexus.auth;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Sets a date of birth only where there is none, in one statement. Two requests racing to
     * set it cannot both win, which a read-then-write could let happen.
     *
     * @return 1 if this set it, 0 if the account already had one
     */
    @Modifying
    @Query("UPDATE AppUser u SET u.dateOfBirth = :dateOfBirth WHERE u.id = :id AND u.dateOfBirth IS NULL")
    int declareDateOfBirthIfUnset(@Param("id") long id, @Param("dateOfBirth") LocalDate dateOfBirth);
}

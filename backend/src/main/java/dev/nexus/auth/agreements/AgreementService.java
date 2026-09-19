package dev.nexus.auth.agreements;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a reader still owes, and the recording of what they have given.
 *
 * <p>Every method takes the owner's id from its caller's authenticated principal and filters
 * on it. Nothing here reads or writes a row for an id that arrived in a request body.
 *
 * <p>Web and native are kept apart throughout: an acceptance is (user, document, platform),
 * and a lookup for one platform never sees the other's rows. Accepting in a browser therefore
 * leaves the phone still asking, which is the intended product behaviour and not an oversight.
 */
@Service
public class AgreementService {

    private final AgreementAcceptanceRepository acceptances;
    private final AgreementProperties properties;

    public AgreementService(AgreementAcceptanceRepository acceptances, AgreementProperties properties) {
        this.acceptances = acceptances;
        this.properties = properties;
    }

    /** Everything that applies to a platform, whether or not anyone has accepted it. Public. */
    public List<AgreementResponse> current(AgreementPlatform platform) {
        return Agreement.forPlatform(platform).stream()
                .map(document -> AgreementResponse.of(document, properties.of(document)))
                .toList();
    }

    /**
     * What this account has not accepted at its current version, for the platform it is
     * calling from. An empty list means they are clear and nothing should be put in their way.
     */
    @Transactional(readOnly = true)
    public List<AgreementResponse> outstanding(Long userId, AgreementPlatform platform) {
        Set<String> held = acceptances.findByUserIdAndPlatform(userId, platform).stream()
                .map(row -> key(row.getDocument(), row.getVersion()))
                .collect(Collectors.toSet());

        return Agreement.forPlatform(platform).stream()
                .filter(document -> !held.contains(key(document, properties.versionOf(document))))
                .map(document -> AgreementResponse.of(document, properties.of(document)))
                .toList();
    }

    /**
     * Records a set of acceptances, refusing any that is not current or does not belong on
     * this platform. Checked in full before anything is written, so a request carrying one bad
     * pair records none of itself rather than half.
     */
    @Transactional
    public void accept(Long userId, AgreementPlatform platform, List<AcceptedAgreement> agreements) {
        Instant now = Instant.now();

        for (AcceptedAgreement sent : agreements) {
            if (!sent.document().appliesTo(platform)) {
                throw AgreementRejectedException.notApplicable(sent.document(), platform);
            }
            String current = properties.versionOf(sent.document());
            if (!current.equals(sent.version())) {
                throw AgreementRejectedException.staleVersion(sent.document(), sent.version(), current);
            }
        }

        agreements.forEach(sent -> record(userId, platform, sent.document(), sent.version(), now));
    }

    /**
     * What registration does for a caller that sent only the old {@code acceptedTerms}
     * boolean.
     *
     * <p>Exactly the two documents that box named, on the platform the caller declared, and
     * nothing else. An EULA and a cookie notice were never on that form, so they are not
     * written here — they fall outstanding and are put to the reader at their next sign-in.
     * Inventing them would be recording consent to a text nobody was shown.
     */
    @Transactional
    public void acceptTermsAndPrivacy(Long userId, AgreementPlatform platform, Instant at) {
        record(userId, platform, Agreement.TERMS, properties.versionOf(Agreement.TERMS), at);
        record(userId, platform, Agreement.PRIVACY, properties.versionOf(Agreement.PRIVACY), at);
    }

    /**
     * Insert-if-absent. A client that retries a POST it already made, or a reader who accepts
     * the same text twice on two devices, must leave one row and not two — the unique
     * constraint in V27 says the same thing at the database.
     */
    private void record(
            Long userId, AgreementPlatform platform, Agreement document, String version, Instant at) {
        if (acceptances.existsByUserIdAndDocumentAndPlatformAndVersion(userId, document, platform, version)) {
            return;
        }
        acceptances.save(new AgreementAcceptance(userId, document, platform, version, at));
    }

    private String key(Agreement document, String version) {
        return document.name() + '@' + version;
    }
}

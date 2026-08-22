package dev.nexus.core.importing;

import java.util.List;

/**
 * What an import did, including what it could not place.
 *
 * <p>The unmatched list is the designed escape hatch rather than a failure signal: no
 * cross-catalogue mapping is ever complete, and telling the user which titles were skipped
 * beats silently dropping them.
 */
public record ImportReport(int created, int updated, List<UnmatchedItem> unmatched) {

    public record UnmatchedItem(String providerItemId, String title, String reason) {}

    public int total() {
        return created + updated + unmatched.size();
    }
}

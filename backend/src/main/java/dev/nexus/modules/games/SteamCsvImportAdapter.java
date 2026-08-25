package dev.nexus.modules.games;

import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvImportAdapter;
import dev.nexus.core.importing.CsvStatuses;
import dev.nexus.core.importing.CsvTable;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads a Steam library from a CSV.
 *
 * <p>Steam publishes no export of its own, so this reads what the community exporters write:
 * an appid, a name, and playtime. The appid is what matters — {@link SteamToIgdbResolver}
 * joins on it exactly as it does for the API import, so a CSV row resolves as well as a
 * pulled one. A row without an appid is useless to that join and is skipped.
 *
 * <p>Playtime arrives in either unit depending on the exporter: {@code playtime_forever} is
 * minutes, while a column headed "hours" is hours. Both are stored as minutes, which is what
 * the games module counts in.
 */
@Component
public class SteamCsvImportAdapter implements CsvImportAdapter {

    private static final int MINUTES_PER_HOUR = 60;

    @Override
    public Provider provider() {
        return Provider.STEAM;
    }

    @Override
    public List<ImportedEntry> parse(CsvTable table) {
        if (!table.has("appid", "app id", "steam appid", "steam_appid")) {
            throw new CsvFormatException(
                    "That file has no appid column. A Steam export needs one column of app ids, "
                            + "and usually has a name and playtime beside it.");
        }

        List<ImportedEntry> entries = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            String appId = row.value("appid", "app id", "steam appid", "steam_appid");
            if (appId == null) {
                continue;
            }

            Integer minutes = minutesPlayed(row);
            String title = row.value("name", "title", "game", "game name");

            entries.add(new ImportedEntry(
                    new ExternalItemRef(Provider.STEAM, appId, title == null ? appId : title),
                    // Same reading as the API import: Steam knows only how long something was
                    // played, so anything touched counts as in progress and the rest as backlog.
                    status(row, minutes),
                    minutes,
                    // Playtime has no maximum, which is exactly why progress_max is nullable.
                    null,
                    minutes == null ? null : ProgressUnit.MINUTES,
                    null,
                    null,
                    null,
                    null));
        }
        return entries;
    }

    /**
     * A status column is honoured when the export has one — some exporters carry a shelf the
     * reader arranged by hand, and that is worth more than what playtime implies.
     */
    private TrackingStatus status(CsvTable.Row row, Integer minutes) {
        TrackingStatus fromPlaytime =
                minutes != null && minutes > 0 ? TrackingStatus.IN_PROGRESS : TrackingStatus.PLANNING;
        return CsvStatuses.of(row.value("status", "shelf", "category"), fromPlaytime);
    }

    private Integer minutesPlayed(CsvTable.Row row) {
        Integer minutes = row.number("playtime_forever", "playtime minutes", "minutes played", "minutes");
        if (minutes != null) {
            return minutes;
        }
        Integer hours = row.number("hours played", "hours", "playtime hours", "playtime");
        return hours == null ? null : hours * MINUTES_PER_HOUR;
    }
}

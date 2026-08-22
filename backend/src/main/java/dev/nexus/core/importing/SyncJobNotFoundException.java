package dev.nexus.core.importing;

/** Unknown job, or one belonging to another user. Both answer 404. */
public class SyncJobNotFoundException extends RuntimeException {

    public SyncJobNotFoundException() {
        super("No such sync job.");
    }
}

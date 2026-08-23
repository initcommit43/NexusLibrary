package dev.nexus.core.importing;

/**
 * A failure the reader can do something about — a private Steam profile, a revoked token —
 * as opposed to one they can only retry.
 *
 * <p>Exists because an import runs in the background: its failure never reaches an HTTP
 * status, so the advice has to travel on the job instead. Without this the runner would have
 * to name particular modules' exceptions, which core has no business knowing.
 */
public interface UserFixableException {

    /** What to tell the reader, in terms of what they can change. */
    String advice();
}

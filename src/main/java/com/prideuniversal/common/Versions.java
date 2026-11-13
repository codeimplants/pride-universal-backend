package com.prideuniversal.common;

import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Objects;

/**
 * The explicit half of optimistic locking (see {@code FlatService.update()}): a
 * missing version is a 400, a stale one a 409. The entity's {@code @Version}
 * column is the other half, catching the race this check cannot.
 */
public final class Versions {

    private Versions() {}

    public static void requireCurrent(Long current, Long sent, String what) {
        if (sent == null) {
            throw ApiException.badRequest(
                    "version is required when updating a " + what
                            + ". Re-fetch it and send the version it returned.");
        }
        if (!Objects.equals(current, sent)) {
            throw new OptimisticLockingFailureException(what + " was modified by someone else");
        }
    }
}

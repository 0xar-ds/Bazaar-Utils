package com.github.mkram17.bazaarutils.utils.bazaar.data;

/** Polling and scheduling constants for {@link BazaarDataManager}. */
public final class BazaarDataSettings {
    private BazaarDataSettings() {}

    static final long BASE_INTERVAL_MS = 20_000;
    static final long POST_OFFSET_MS = 500;
    static final long STALE_BACKOFF_MS = 750;
    static final long FAILURE_RETRY_MS = 500;
    static final int STALE_WARNING_THRESHOLD = 5;
}
package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.data.APIUtils;
import com.github.mkram17.bazaarutils.events.BazaarDataUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceLevelPool;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;
import static com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataSettings.*;

public final class BazaarDataManager {

    @Getter
    public enum PriceType {
        INSTABUY,
        INSTASELL;

        public PriceType opposite() {
            return this == INSTABUY ? INSTASELL : INSTABUY;
        }

        public String getString() {
            return switch (this) {
                case INSTASELL -> "Buy";
                case INSTABUY -> "Sell";
            };
        }
    }

    @Getter private static volatile CustomBazaarReply currentReply;
    @Getter private static volatile long lastSnapshotTs = -1;
    private static volatile long lastFetchWallClock = -1;

    private static volatile ScheduledFuture<?> scheduledTask;
    private static final Object SCHED_LOCK = new Object();

    private static final AtomicInteger consecutiveIdenticalSnapshots = new AtomicInteger(0);
    private static final AtomicInteger consecutiveFailures           = new AtomicInteger(0);

    static volatile Map<String, String> nameToProductIdCache = Map.of();
    @Setter static volatile boolean conversionsLoaded = false;

    @RunOnInit
    public static void init() {
        scheduleFetch(0);
        PlayerActionUtil.notifyAll("BazaarDataManager initialized (simple fixed-interval poller). Base=" + BASE_INTERVAL_MS + "ms", NotificationType.BAZAARDATA);
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    private static void scheduleFetch(long delayMs) {
        synchronized (SCHED_LOCK) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            
            scheduledTask = BazaarUtils.BUExecutorService.schedule(BazaarDataManager::fetchOnceSafely, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void fetchOnceSafely() {
        try {
            fetchOnce();
        } catch (Throwable t) {
            Util.notifyError("Unexpected error in BazaarDataManager fetch loop", t);
            scheduleFetch(FAILURE_RETRY_MS);
        }
    }

    private static void fetchOnce() {
        lastFetchWallClock = System.currentTimeMillis();
        APIUtils.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
            if (throwable != null) {
                consecutiveFailures.incrementAndGet();
                PlayerActionUtil.notifyAll(
                        "Fetch failure (" + throwable.getClass().getSimpleName()
                                + "). Retry in " + FAILURE_RETRY_MS + "ms (failures=" + consecutiveFailures.get() + ")",
                        NotificationType.BAZAARDATA);
                scheduleFetch(FAILURE_RETRY_MS);
                return;
            }
            if (reply == null || !reply.isSuccess()) {
                consecutiveFailures.incrementAndGet();
                PlayerActionUtil.notifyAll(
                        "Null/unsuccessful reply. Retry in " + FAILURE_RETRY_MS + "ms (failures=" + consecutiveFailures.get() + ")",
                        NotificationType.BAZAARDATA);
                scheduleFetch(FAILURE_RETRY_MS);
                return;
            }
            consecutiveFailures.set(0);

            // Mixin cast happens once here — CustomBazaarReply owns lastUpdated as a plain field.
            CustomBazaarReply wrapped = CustomBazaarReply.fromSkyBlockReply(reply);
            long snapshotTs = wrapped.getLastUpdated();

            if (snapshotTs <= 0) {
                PlayerActionUtil.notifyAll("Invalid lastUpdated <= 0. Retry in " + FAILURE_RETRY_MS + "ms", NotificationType.BAZAARDATA);
                scheduleFetch(FAILURE_RETRY_MS);
                return;
            }

            if (snapshotTs != lastSnapshotTs) {
                long previous  = lastSnapshotTs;
                lastSnapshotTs = snapshotTs;
                currentReply   = wrapped;
                consecutiveIdenticalSnapshots.set(0);

                try {
                    ingestSnapshot(wrapped, snapshotTs);
                } catch (Throwable t) {
                    Util.notifyError("ingestSnapshot failed for snapshot " + snapshotTs, t);
                }

                // Legacy event — kept until remaining call sites are migrated.
                try {
                    EVENT_BUS.post(new BazaarDataUpdateEvent(reply));
                } catch (Throwable t) {
                    Util.notifyError("BazaarDataUpdateEvent post failed", t);
                }

                if (previous != -1) {
                    PlayerActionUtil.notifyAll("New snapshot " + snapshotTs + " (Δ " + (snapshotTs - previous) + " ms). Scheduling next predicted fetch.", NotificationType.BAZAARDATA);
                } else {
                    PlayerActionUtil.notifyAll("First snapshot " + snapshotTs + " received.", NotificationType.BAZAARDATA);
                }

                scheduleNextFromSnapshot(snapshotTs);
            } else {
                int identical = consecutiveIdenticalSnapshots.incrementAndGet();
                PlayerActionUtil.notifyAll("Snapshot unchanged (" + snapshotTs + ") x" + identical, NotificationType.BAZAARDATA);
                if (identical == STALE_WARNING_THRESHOLD) {
                    PlayerActionUtil.notifyAll("WARNING: " + identical + " identical snapshots in a row. Server might be lagging or BASE_INTERVAL_MS too short.", NotificationType.BAZAARDATA);
                }
                scheduleNextFromSnapshot(snapshotTs);
            }
        });
    }

    private static void scheduleNextFromSnapshot(long snapshotTs) {
        long now = System.currentTimeMillis();
        long target = snapshotTs + BASE_INTERVAL_MS + POST_OFFSET_MS;

        long delay;
        if (now >= target) {
            // Past the ideal fetch time; server hasn’t advanced snapshot yet. Don’t spam: back off.
            delay = STALE_BACKOFF_MS;
        } else {
            var typicalDelay = target - now;
            delay = Math.max(typicalDelay, STALE_BACKOFF_MS);
        }
        scheduleFetch(delay);
    }

    // ── Ingestion ─────────────────────────────────────────────────────────────

    private static void ingestSnapshot(CustomBazaarReply reply, long snapshotTs) {
        Map<String, SkyBlockBazaarReply.Product> products = reply.getProducts();
        if (products == null) return;

        DataSource.ApiSnapshot source = new DataSource.ApiSnapshot(snapshotTs);

        var snapshot = new HashMap<String, Map.Entry<List<PriceLevelPool>, List<PriceLevelPool>>>(products.size());
        for (var entry : products.entrySet()) {
            String productId = entry.getKey();
            var    product   = entry.getValue();
            if (product == null) continue;

            snapshot.put(productId, Map.entry(
                    parseSummary(product.getBuySummary(),  source),
                    parseSummary(product.getSellSummary(), source)));
        }

        BazaarProductRegistry.notifyApiSnapshotBatch(snapshot, snapshotTs);
    }

    private static List<PriceLevelPool> parseSummary(List<SkyBlockBazaarReply.Product.Summary> summaries, DataSource.ApiSnapshot source) {
        if (summaries == null || summaries.isEmpty()) return List.of();

        return summaries.stream()
                .map(summary -> PriceLevelPool.fromApiSummary(summary, source))
                .toList();
    }

    // ── Legacy read methods (kept until call sites migrate to registry) ────────

    public static OptionalInt getOrderCountOptional(String productId, OrderType orderType, double price) {
        CustomBazaarReply reply = currentReply;
        PriceType priceType = orderType.asPriceType();

        if (reply == null || productId == null || priceType == null) return OptionalInt.empty();

        try {
            SkyBlockBazaarReply.Product product = reply.getProduct(productId);

            if (product == null) {
                return OptionalInt.empty();
            }

            List<SkyBlockBazaarReply.Product.Summary> list = switch (priceType) {
                case INSTABUY -> product.getBuySummary();
                case INSTASELL -> product.getSellSummary();
            };

            if (list == null) {
                return OptionalInt.empty();
            }

            for (SkyBlockBazaarReply.Product.Summary s : list) {
                if (Double.compare(s.getPricePerUnit(), price) == 0) {
                    return OptionalInt.of((int) s.getOrders());
                }
            }

            return OptionalInt.of(0);
        } catch (Exception e) {
            Util.notifyError("Error in getOrderCountOptional for productId=" + productId, e);

            return OptionalInt.empty();
        }
    }

    public static OptionalDouble findItemPriceOptional(String productId, OrderType orderType) {
        CustomBazaarReply reply    = currentReply;
        PriceType         priceType = orderType.asPriceType();
        if (reply == null || productId == null || priceType == null) return OptionalDouble.empty();
        try {
            var product = reply.getProduct(productId);
            if (product == null) return OptionalDouble.empty();
            return switch (priceType) {
                case INSTABUY -> {
                    var list = product.getBuySummary();
                    yield (list == null || list.isEmpty()) ? OptionalDouble.of(0.0)
                            : OptionalDouble.of(list.getFirst().getPricePerUnit());
                }
                case INSTASELL -> {
                    var list = product.getSellSummary();
                    yield (list == null || list.isEmpty()) ? OptionalDouble.of(0.0)
                            : OptionalDouble.of(list.getFirst().getPricePerUnit());
                }
            };
        } catch (Exception e) {
            Util.notifyError("Error in findItemPriceOptional for productId=" + productId, e);
            return OptionalDouble.empty();
        }
    }

    public static Optional<String> findProductIdOptional(String naturalName) {
        if (naturalName == null || naturalName.isBlank()) return Optional.empty();
        ensureConversionsLoaded();
        return Optional.ofNullable(nameToProductIdCache.get(naturalName.toLowerCase(Locale.ROOT)));
    }

    /**
     * Cached conversion load. Thread-safe (single pass).
     */
    private static void ensureConversionsLoaded() {
        if (conversionsLoaded) {
            return;
        }

        synchronized (BazaarDataManager.class) {
            if (conversionsLoaded) {
                return;
            }

            try {
                Map<String, String> mutable = new HashMap<>();

                var resources = ResourceManager.getResourceJson();
                var conversions = resources.getAsJsonObject();

                for (String key : conversions.keySet()) {
                    String value = conversions.get(key).getAsString();
                    if (value != null) {
                        mutable.put(value.toLowerCase(Locale.ROOT), key);
                    }
                }

                nameToProductIdCache = Collections.unmodifiableMap(mutable);
                conversionsLoaded = true;

                PlayerActionUtil.notifyAll("Loaded bazaarConversions cache: " + nameToProductIdCache.size() + " entries.", NotificationType.BAZAARDATA);
            } catch (Exception e) {
                Util.notifyError("Failed loading bazaarConversions cache", e);

                nameToProductIdCache = Map.of();
                conversionsLoaded = true;
            }
        }
    }

    static Map<String, String> getNameToProductIdCache() {
        ensureConversionsLoaded();
        return nameToProductIdCache;
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    public static Optional<Duration> getCurrentSnapshotAge() {
        long ts = lastSnapshotTs;

        if (ts <= 0) {
            return Optional.empty();
        }

        return Optional.of(Duration.ofMillis(System.currentTimeMillis() - ts));
    }

    public static Optional<Duration> getTimeSinceLastFetchAttempt() {
        long f = lastFetchWallClock;

        if (f <= 0) {
            return Optional.empty();
        }

        return Optional.of(Duration.ofMillis(System.currentTimeMillis() - f));
    }
}
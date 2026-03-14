package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceLevelPool;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Per-product mutable order book. Pure market data, no player-order tracking.
 *
 * <p>Player position queries use {@link #bookPositionOf(TransactionType.Side, double)}
 * directly with the price from a {@code TrackedPlayerOrder}. The registry owns all
 * cross-concern logic (which orders are active, what prices they sit at).
 */
public final class BazaarProductData {
    public final String productId;

    // buyBook  = sell offers (asks) — natural order,  firstEntry = lowest ask
    // sellBook = buy orders  (bids) — reverse order,  firstEntry = highest bid
    public final NavigableMap<Double, PriceLevelPool> buyBook = new ConcurrentSkipListMap<>();
    public final NavigableMap<Double, PriceLevelPool> sellBook = new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    public volatile long lastApiUpdateTs = -1;
    public volatile long lastBookScreenTs = -1;

    public BazaarProductData(String productId) {
        this.productId = productId;
    }

    public NavigableMap<Double, PriceLevelPool> bookFor(TransactionType.Side side) {
        return side == TransactionType.Side.BUY ? buyBook : sellBook;
    }

    public NavigableMap<Double, PriceLevelPool> bookFor(TransactionType transaction) {
        return transaction.getPriceType() == BazaarDataManager.PriceType.INSTABUY ? buyBook : sellBook;
    }

    /**
     * Merges a placement into the book. Returns {@code true} — placement always
     * mutates the pool. The registry is responsible for firing pool events.
     */
    public boolean applyPlacement(TransactionType.Side side, double price, int amount) {
        long now = System.currentTimeMillis();
        var book = bookFor(new TransactionType(side, TransactionType.Method.ORDER));

        book.merge(price, new PriceLevelPool(price, amount, 1, now, new DataSource.OrderPlaced(now)), (existing, next) -> existing.withPlacementIncrement(amount, now));

        return true;
    }

    /**
     * Merges incoming price levels. Returns {@code true} if any level was added
     * or superseded.
     */
    public boolean splice(TransactionType.Side side, List<PriceLevelPool> incoming) {
        var book = bookFor(side);

        boolean changed = false;

        for (PriceLevelPool next : incoming) {
            PriceLevelPool[] chosen = {null};

            book.merge(next.price(), next, (current, it) -> {
                if (current.source().isSupersededBy(it.source())) {
                    chosen[0] = it;
                    return it;
                }
                chosen[0] = current;
                return current;
            });

            if (chosen[0] == null || chosen[0] == next) changed = true;
        }

        return changed;
    }

    /**
     * Removes stale API-sourced levels not present in the current snapshot.
     * Levels at {@code activeTrackedPrices} are never orphaned.
     *
     * @return {@code true} if any level was removed
     */
    public boolean orphanStaleApiEntries(TransactionType.Side side, Set<Double> currentPrices, Set<Double> activeTrackedPrices, long currentSnapshotTs) {
        return bookFor(side).entrySet().removeIf(entry ->
                entry.getValue().source() instanceof DataSource.ApiSnapshot(long snapshotTs)
                        && snapshotTs < currentSnapshotTs
                        && !currentPrices.contains(entry.getKey())
                        && !activeTrackedPrices.contains(entry.getKey()));
    }

    /**
     * Decrements volume at the given price level, removing the entry if volume
     * reaches zero. Returns {@code true} if the level existed and was mutated.
     * The registry is responsible for firing pool events.
     */
    public boolean applyVolumeDecrement(TransactionType.Side side, double price, long amount) {
        var book = bookFor(new TransactionType(side, TransactionType.Method.ORDER));

        if (!book.containsKey(price)) return false;

        book.computeIfPresent(price, (__, pool) -> {
            PriceLevelPool updated = pool.withVolumeDecrement(amount);

            return updated.totalVolume() <= 0 ? null : updated;
        });

        return true;
    }

    /**
     * How many price levels are strictly ahead of the given price — 0 means competitive.
     */
    public int bookPositionOf(TransactionType.Side side, double price) {
        return bookFor(new TransactionType(side, TransactionType.Method.ORDER)).headMap(price).size();
    }
}
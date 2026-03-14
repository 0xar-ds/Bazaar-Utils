package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSource;

import java.util.Set;

/**
 * Fired once after a full API snapshot is processed, carrying only the product
 * IDs whose price pool actually changed. Subscribers should check
 * {@link #changedProductIds()} before doing any work.
 *
 * <p>Use this instead of reacting to 1800 individual
 * {@link BazaarPricePoolUpdateEvent}s on every API poll.
 *
 * @param changedProductIds  products whose pool shifted in this snapshot
 * @param source             the snapshot that triggered the update
 */
public record BazaarPricePoolBatchUpdateEvent(Set<String> changedProductIds, DataSource source) {
    /** Convenience — returns true if this product was affected. */
    public boolean affects(String productId) {
        return changedProductIds.contains(productId);
    }
}
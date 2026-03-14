package com.github.mkram17.bazaarutils.events;

import com.github.mkram17.bazaarutils.utils.bazaar.data.DataSource;

/**
 * Fired by {@code BazaarProductRegistry} whenever a product's price pool is
 * mutated — after any splice, placement, or volume decrement that could shift
 * the best available price for a product.
 *
 * <p>Subscribers that care about derived state (e.g. order position relative
 * to market) should react to this event and recompute from the registry
 * directly rather than receiving a pre-computed conclusion.
 *
 * @param productId  the product whose pool was updated
 * @param source     the data source that triggered the update
 */
public record BazaarPricePoolUpdateEvent(String productId, DataSource source) {}
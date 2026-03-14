package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderState;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TrackedPlayerOrder;
import com.github.mkram17.bazaarutils.utils.storage.DataStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class TrackedOrdersStorage {
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private static final Type TYPE = new TypeToken<List<TrackedPlayerOrder>>(){}.getType();

    public static final DataStorage<List<TrackedPlayerOrder>> INSTANCE = new DataStorage<>(ArrayList::new, "tracked_orders", TYPE);

    private TrackedOrdersStorage() {}

    @RunOnInit
    public static void seedRegistry() {
        List<TrackedPlayerOrder> loaded = INSTANCE.get();

        // Find highest persisted id to seed the sequence above it.
        long maxId = loaded.stream()
                .mapToLong(TrackedPlayerOrder::id)
                .max()
                .orElse(0L);

        ID_SEQ.set(maxId + 1);

        // Fix any records that predate the id field (GSON deserializes as 0).
        boolean needsResave = loaded.stream().anyMatch(o -> o.id() == 0);

        if (needsResave) {
            INSTANCE.set(loaded.stream()
                    .map(order -> order.id() == 0 ? order.withId(nextId()) : order)
                    .toList());
            INSTANCE.save();
        }

        // Seed registry entries for active orders.
        INSTANCE.get().stream()
                .filter(order -> order.state() instanceof OrderState.Set
                        || order.state() instanceof OrderState.Partial
                        || order.state() instanceof OrderState.Filled)
                .map(TrackedPlayerOrder::productId)
                .distinct()
                .forEach(BazaarProductRegistry::getOrCreate);
    }

    public static long nextId() {
        return ID_SEQ.getAndIncrement();
    }

    public static void persist() {
        INSTANCE.set(
                INSTANCE.get()
                        .stream()
                        .filter(order -> !(order.state() instanceof OrderState.Claimed) && !(order.state() instanceof OrderState.Cancelled))
                        .toList()
        );
        INSTANCE.save();
    }
}
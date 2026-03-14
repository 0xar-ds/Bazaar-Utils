package com.github.mkram17.bazaarutils.utils.bazaar.data;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.data.TrackedOrdersStorage;
import com.github.mkram17.bazaarutils.events.BazaarPricePoolBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.BazaarPricePoolUpdateEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ResourceManager;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.components.ManageOrdersParser;
import com.github.mkram17.bazaarutils.utils.bazaar.components.OrdersScreenParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderState;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TrackedPlayerOrder;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PriceLevelPool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public final class BazaarProductRegistry {
    private static final ConcurrentHashMap<String, BazaarProductData> REGISTRY = new ConcurrentHashMap<>();

    private static volatile boolean conversionsLoaded = false;
    private static volatile Map<String, String> nameToProductIdCache = Map.of();

    private BazaarProductRegistry() {}

    public static Collection<String> getAllProductIds() {
        return REGISTRY.keySet();
    }

    public static BazaarProductData getOrCreate(String productId) {
        return REGISTRY.computeIfAbsent(productId, BazaarProductData::new);
    }

    // ── Write: OrderPlaced ────────────────────────────────────────────────────

    public static TrackedPlayerOrder notifyPlacement(String productId, TransactionType.Side side, double price, int amount) {
        price = Util.truncateNum(price);
        long now = System.currentTimeMillis();
        
        var data = getOrCreate(productId);

        var order = new TrackedPlayerOrder(
                TrackedOrdersStorage.nextId(), productId, side, price,
                amount, 0, 0, TrackedPlayerOrder.UNANCHORED,
                new OrderState.Set(), now, now);

        data.applyPlacement(side, price, amount);

        var withNew = Stream.concat(TrackedOrdersStorage.INSTANCE.get().stream(), Stream.of(order)).toList();
        var reindexed = reindexActive(withNew);
        var placed = reindexed.stream().filter(o -> o.id() == order.id()).findFirst().orElse(order);

        TrackedOrdersStorage.INSTANCE.set(reindexed);
        TrackedOrdersStorage.persist();

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.OrderPlaced(now)));
        PlayerActionUtil.notifyAll("Placement: " + describeOrder(placed) + " | pool @ " + price + " → " + describeSource(data.bookFor(new TransactionType(side, TransactionType.Method.ORDER)).get(price).source()), NotificationType.BAZAARDATA);

        return placed;
    }

    // ── Write: ItemSummary ────────────────────────────────────────────────────

    public static void notifyBookScreen(String productId, List<PriceLevelPool> buyLevels, List<PriceLevelPool> sellLevels) {
        long now = System.currentTimeMillis();
        var data = getOrCreate(productId);

        data.splice(TransactionType.Side.BUY,  buyLevels);
        data.splice(TransactionType.Side.SELL, sellLevels);
        data.lastBookScreenTs = now;

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.ItemSummary(now)));
        PlayerActionUtil.notifyAll(
                "ItemSummary: " + productId
                        + " | buy="  + buyLevels.size()  + " levels"
                        + " | sell=" + sellLevels.size() + " levels"
                        + " | best buy="  + (data.buyBook.isEmpty()  ? "none" : data.buyBook.firstEntry().getValue().price())
                        + " | best sell=" + (data.sellBook.isEmpty() ? "none" : data.sellBook.firstEntry().getValue().price()),
                NotificationType.BAZAARDATA);
    }

    // ── Write: ManageOrdersItemPage (confirm / evict) ─────────────────────────

    public static void notifyManageOrdersItemPage(String productId, List<ManageOrdersParser.ParsedOrderHint> hints, long observedAt) {
        long now = System.currentTimeMillis();

        final double EPSILON = 1;

        var current = TrackedOrdersStorage.INSTANCE.get();
        var forProduct = current.stream().filter(order -> order.productId().equals(productId)).toList();
        var touched = new HashSet<Long>();

        hints.stream()
                .map(hint -> forProduct.stream()
                        .filter(order -> !touched.contains(order.id()))
                        .filter(order -> order.side() == hint.side())
                        .filter(order -> order.originalAmount() == hint.amount())
                        .filter(order -> Math.abs(order.price() - hint.price()) < EPSILON)
                        .findFirst()
                        .orElse(null))
                .forEach(match -> {
                    if (match != null) {
                        touched.add(match.id());
                        PlayerActionUtil.notifyAll("ManageOrders confirm: " + describeOrder(match), NotificationType.BAZAARDATA);
                    }
                });

        hints.stream()
                .filter(hint -> forProduct.stream()
                        .filter(order -> touched.contains(order.id()))
                        .noneMatch(order -> order.side() == hint.side()
                                && order.originalAmount() == hint.amount()
                                && Double.compare(order.price(), hint.price()) == 0))
                .forEach(hint -> PlayerActionUtil.notifyAll(
                        "ManageOrders unmatched hint: "
                                + hint.side().getString() + " " + hint.amount()
                                + "x " + productId + " @ " + hint.price()
                                + " (no tracked order — skipped)",
                        NotificationType.BAZAARDATA));

        forProduct.stream()
                .filter(order -> !touched.contains(order.id()))
                .filter(order -> order.lastUpdatedAt() <= observedAt)
                .forEach(order -> PlayerActionUtil.notifyAll("ManageOrders evict: " + describeOrder(order), NotificationType.BAZAARDATA));

        var unrelated = current.stream().filter(order -> !order.productId().equals(productId)).toList();
        var kept = forProduct.stream()
                .filter(order -> touched.contains(order.id()) || order.lastUpdatedAt() > observedAt)
                .map(order -> touched.contains(order.id())
                        ? new TrackedPlayerOrder(order.id(), order.productId(), order.side(), order.price(),
                        order.originalAmount(), order.filledAmount(), order.claimedAmount(),
                        order.lastKnownIndex(), order.state(), order.placedAt(), now)
                        : order)
                .toList();

        TrackedOrdersStorage.INSTANCE.set(Stream.concat(unrelated.stream(), kept.stream()).toList());
        TrackedOrdersStorage.persist();

        if (!touched.isEmpty()) {
            EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.ManageOrders(now)));
        }
    }

    // ── Write: ManageOrdersGroupPage (confirm only) ───────────────────────────

    public static void notifyManageOrdersGroupPage(List<ManageOrdersParser.ParsedOrderHint> hints, long observedAt) {
        long now = System.currentTimeMillis();

        final double EPSILON = 1;

        var current = TrackedOrdersStorage.INSTANCE.get();
        var touched = new HashSet<Long>();
        var touchedProducts = new HashSet<String>();

        hints.stream()
                .map(hint -> current.stream()
                        .filter(order -> !touched.contains(order.id()))
                        .filter(order -> order.productId().equals(hint.productId()))
                        .filter(order -> order.side() == hint.side())
                        .filter(order -> order.originalAmount() == hint.amount())
                        .filter(order -> Math.abs(order.price() - hint.price()) < EPSILON)
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .forEach(match -> {
                    touched.add(match.id());
                    touchedProducts.add(match.productId());
                    PlayerActionUtil.notifyAll("ManageOrders confirm: " + describeOrder(match), NotificationType.BAZAARDATA);
                });

        var result = current.stream()
                .map(order -> touched.contains(order.id())
                        ? new TrackedPlayerOrder(order.id(), order.productId(), order.side(), order.price(),
                        order.originalAmount(), order.filledAmount(), order.claimedAmount(),
                        order.lastKnownIndex(), order.state(), order.placedAt(), now)
                        : order)
                .toList();

        TrackedOrdersStorage.INSTANCE.set(result);
        TrackedOrdersStorage.persist();

        touchedProducts.forEach(productId -> EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.ManageOrders(now))));
    }

    // ── Write: ApiSnapshot ────────────────────────────────────────────────────

    public static void notifyApiSnapshotBatch(Map<String, Map.Entry<List<PriceLevelPool>, List<PriceLevelPool>>> snapshot, long snapshotTs) {
        var activeTrackedIndex = buildActiveTrackedIndex();
        var changed = new HashSet<String>();
        var allInferredFills = new ArrayList<TrackedPlayerOrder>();

        // Snapshot storage once — all fill inference reads from this consistent view.
        var storageSnapshot = TrackedOrdersStorage.INSTANCE.get();

        for (var entry : snapshot.entrySet()) {
            String productId = entry.getKey();
            var data = getOrCreate(productId);
            var buyLevels = entry.getValue().getKey();
            var sellLevels = entry.getValue().getValue();

            var pricesBuy = priceSet(buyLevels);
            var pricesSell = priceSet(sellLevels);

            var activeBuy = activeTrackedIndex.getOrDefault(productId, Map.of()).getOrDefault(TransactionType.Side.BUY,  Set.of());
            var activeSell = activeTrackedIndex.getOrDefault(productId, Map.of()).getOrDefault(TransactionType.Side.SELL, Set.of());

            boolean poolChanged = data.splice(TransactionType.Side.BUY,  buyLevels);
            poolChanged |= data.splice(TransactionType.Side.SELL, sellLevels);
            poolChanged |= data.orphanStaleApiEntries(TransactionType.Side.BUY,  pricesBuy,  activeSell, snapshotTs);
            poolChanged |= data.orphanStaleApiEntries(TransactionType.Side.SELL, pricesSell, activeBuy,  snapshotTs);
            data.lastApiUpdateTs = snapshotTs;

            if (poolChanged) changed.add(productId);

            // Only infer fills once at least one prior snapshot has been processed.
            if (data.lastApiUpdateTs <= 0) continue;

            storageSnapshot.stream()
                    .filter(order -> order.productId().equals(productId))
                    .filter(order -> order.state() instanceof OrderState.Set || order.state() instanceof OrderState.Partial)
                    .forEach(order -> {
                        Set<Double> snapshotPrices = order.side() == TransactionType.Side.BUY ? pricesSell : pricesBuy;
                        var book = data.bookFor(new TransactionType(order.side(), TransactionType.Method.ORDER));

                        if (!snapshotPrices.contains(order.price()) && !book.containsKey(order.price())) {
                            var filled = order.withFill(order.originalAmount() - order.filledAmount());
                            allInferredFills.add(filled);
                            changed.add(productId);
                            PlayerActionUtil.notifyAll("ApiSnapshot inferred fill: " + describeOrder(filled) + " | price " + order.price() + " absent from snapshot + pool", NotificationType.BAZAARDATA);
                        }
                    });
        }

        if (!allInferredFills.isEmpty()) {
            var fillIds = allInferredFills.stream()
                    .map(TrackedPlayerOrder::id)
                    .collect(Collectors.toSet());
            var fillMap = allInferredFills.stream()
                    .collect(Collectors.toMap(TrackedPlayerOrder::id, o -> o));

            var withFills = TrackedOrdersStorage.INSTANCE.get().stream()
                    .map(order -> fillIds.contains(order.id()) ? fillMap.get(order.id()) : order)
                    .toList();

            TrackedOrdersStorage.INSTANCE.set(reindexActive(withFills));
            TrackedOrdersStorage.persist();
        }

        if (!changed.isEmpty()) {
            EVENT_BUS.post(new BazaarPricePoolBatchUpdateEvent(Collections.unmodifiableSet(changed), new DataSource.ApiSnapshot(snapshotTs)));
            PlayerActionUtil.notifyAll("ApiSnapshot: " + changed.size() + " products changed", NotificationType.BAZAARDATA);
        }
    }

    // ── Write: OrderFilled (chat — whole order complete, no price) ────────────

    public static void notifyOrderFilled(String productId, TransactionType.Side side, int originalAmount) {
        long now = System.currentTimeMillis();
        var data = REGISTRY.get(productId);
        if (data == null) return;

        var candidates = TrackedOrdersStorage.INSTANCE.get().stream()
                .filter(order -> order.side() == side)
                .filter(order -> order.productId().equals(productId))
                .filter(order -> order.originalAmount() == originalAmount)
                .filter(order -> order.state() instanceof OrderState.Set || order.state() instanceof OrderState.Partial)
                .toList();

        if (candidates.isEmpty()) return;

        // Single-target selection: competitive price first, fallback to best price.
        TrackedPlayerOrder target = candidates.stream()
                .filter(order -> data.bookPositionOf(side, order.price()) == 0)
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .min(side == TransactionType.Side.BUY
                                ? Comparator.comparingDouble(TrackedPlayerOrder::price)
                                : Comparator.comparingDouble(TrackedPlayerOrder::price).reversed())
                        .orElseThrow());

        int unaccounted = target.originalAmount() - target.filledAmount();
        if (unaccounted > 0) data.applyVolumeDecrement(side, target.price(), unaccounted);

        var done = target.withFill(unaccounted);

        var withFill = TrackedOrdersStorage.INSTANCE.get().stream()
                .map(order -> order.id() == target.id() ? done : order)
                .toList();

        // Filled orders move to the front of their group — full reindex required.
        var reindexed = done.state() instanceof OrderState.Filled ? reindexActive(withFill) : withFill;
        var placed = reindexed.stream().filter(o -> o.id() == done.id()).findFirst().orElse(done);

        TrackedOrdersStorage.INSTANCE.set(reindexed);
        TrackedOrdersStorage.persist();

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.OrderFilled(now)));

        PlayerActionUtil.notifyAll("OrderFilled (chat): " + describeOrder(placed), NotificationType.BAZAARDATA);
    }

    // ── Write: SellOrderCancelled ─────────────────────────────────────────────

    public static void notifyCancelSellOffer(String productId, int refundedVolume) {
        long now = System.currentTimeMillis();

        var data = REGISTRY.get(productId);
        if (data == null) return;

        var matched = TrackedOrdersStorage.INSTANCE.get().stream()
                .filter(order -> order.side() == TransactionType.Side.SELL)
                .filter(order -> order.productId().equals(productId))
                .filter(order -> (order.originalAmount() - order.filledAmount()) == refundedVolume)
                .filter(order -> !(order.state() instanceof OrderState.Cancelled))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            PlayerActionUtil.notifyAll("SellCancel: no matching SELL order for " + productId + " remainingVol=" + refundedVolume, NotificationType.BAZAARDATA);
            return;
        }

        data.applyVolumeDecrement(TransactionType.Side.SELL, matched.price(), matched.originalAmount() - matched.claimedAmount());
        cancelAndReindex(matched);

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.OrderCancelled(now)));

        PlayerActionUtil.notifyAll(
                "SellCancel: " + describeOrder(matched.cancelled())
                        + " | remaining=" + refundedVolume + " decremented from pool",
                NotificationType.BAZAARDATA);
    }

    // ── Write: BuyOrderCancelled ──────────────────────────────────────────────

    public static void notifyCancelBuyOrder(double coinsRefunded) {
        long now = System.currentTimeMillis();
        // claimedAmount is always current at cancel time (must claim before cancel).
        // Refund = price × (originalAmount - claimedAmount).
        final double EPSILON = 0.9;

        var matched = TrackedOrdersStorage.INSTANCE.get().stream()
                .filter(order -> order.side() == TransactionType.Side.BUY)
                .filter(order -> !(order.state() instanceof OrderState.Cancelled))
                .filter(order -> Math.abs(order.price() * (order.originalAmount() - order.filledAmount()) - coinsRefunded) < EPSILON)
                .findFirst()
                .orElse(null);

        if (matched == null) {
            PlayerActionUtil.notifyAll("BuyCancel: no tracked BUY order matches coins=" + coinsRefunded, NotificationType.BAZAARDATA);
            return;
        }

        var data = REGISTRY.get(matched.productId());
        if (data == null) return;

        data.applyVolumeDecrement(TransactionType.Side.BUY, matched.price(), matched.originalAmount() - matched.claimedAmount());
        cancelAndReindex(matched);

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(matched.productId(), new DataSource.OrderCancelled(now)));

        PlayerActionUtil.notifyAll(
                "BuyCancel: " + describeOrder(matched)
                        + " | coins=" + coinsRefunded
                        + " | remaining=" + matched.remainingAmount() + " decremented from pool",
                NotificationType.BAZAARDATA);
    }

    // ── Write: OrderClaimed ───────────────────────────────────────────────────

    public static void notifyClaim(String productId, TransactionType.Side side, double price, int claimedAmount) {
        long now = System.currentTimeMillis();

        final double EPSILON = 0.9;

        var data = REGISTRY.get(productId);
        if (data == null) return;

        var candidates = TrackedOrdersStorage.INSTANCE.get().stream()
                .filter(order -> order.productId().equals(productId))
                .filter(order -> order.side() == side)
                .filter(order -> price == 0.0
                        || Double.compare(order.price(), price) == 0
                        || (side == TransactionType.Side.SELL && Math.abs(order.price() * claimedAmount
                        * ((100.0 - BUConfig.USER_BAZAAR_FLIPPER_ACCOUNT_UPGRADE.userBazaarTax) / 100.0)
                        - price) < EPSILON))
                .filter(order -> !(order.state() instanceof OrderState.Claimed))
                .filter(order -> !(order.state() instanceof OrderState.Cancelled))
                .toList();

        if (candidates.isEmpty()) return;

        // Single-target selection: unclaimed fill volume first, then competitive, then best price.
        TrackedPlayerOrder target = candidates.stream()
                .filter(order -> (order.filledAmount() - order.claimedAmount()) >= claimedAmount)
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .filter(order -> data.bookPositionOf(side, order.price()) == 0)
                        .findFirst()
                        .orElseGet(() -> candidates.stream()
                                .min(side == TransactionType.Side.BUY
                                        ? Comparator.comparingDouble(TrackedPlayerOrder::price)
                                        : Comparator.comparingDouble(TrackedPlayerOrder::price).reversed())
                                .orElseThrow()));

        var done = target.withClaim(claimedAmount);
        // Partial claims leave the order alive — no book action needed.
        // Terminal claim: decrement was already applied by the fill; nothing more to do.

        replaceById(target.id(), done);

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.ManageOrders(now)));

        PlayerActionUtil.notifyAll("Claim: " + describeOrder(done), NotificationType.BAZAARDATA);
    }

    // ── Write: InstaDeal ──────────────────────────────────────────────────────

    public static void notifyInstantDeal(String productId, TransactionType.Side consumedSide, double price, long amount) {
        long now = System.currentTimeMillis();
        var data = REGISTRY.get(productId);
        if (data == null) return;

        data.applyVolumeDecrement(consumedSide, price, amount);

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.InstantDeal(now, consumedSide)));

        String playerAction = consumedSide == TransactionType.Side.SELL ? "InstaBuy" : "InstaSell";

        PlayerActionUtil.notifyAll(playerAction + ": " + amount + "x " + productId + " @ " + price + " | consumed from " + consumedSide.getString() + " book", NotificationType.BAZAARDATA);
    }

    // ── Write: OrderFlipped ───────────────────────────────────────────────────

    public static void notifyFlip(String productId, double buyPrice, int unclaimedVolume, double sellPrice) {
        long now = System.currentTimeMillis();

        var data = REGISTRY.get(productId);
        if (data == null) return;

        var current = TrackedOrdersStorage.INSTANCE.get();

        var matchedBuy = current.stream()
                .filter(order -> order.productId().equals(productId))
                .filter(order -> order.side() == TransactionType.Side.BUY)
                .filter(order -> Double.compare(order.price(), buyPrice) == 0)
                .filter(order -> order.state() instanceof OrderState.Filled)
                .filter(order -> order.unclaimedFilled() >= unclaimedVolume)
                .min(Comparator.comparingInt(order -> order.unclaimedFilled() - unclaimedVolume))
                .orElse(null);

        if (matchedBuy == null) {
            PlayerActionUtil.notifyAll("Flip: no matching BUY order found for " + productId + " @ " + buyPrice + " vol=" + unclaimedVolume, NotificationType.BAZAARDATA);
            return;
        }

        var newSell = new TrackedPlayerOrder(
                TrackedOrdersStorage.nextId(), productId, TransactionType.Side.SELL,
                sellPrice, unclaimedVolume, 0, 0, TrackedPlayerOrder.UNANCHORED,
                new OrderState.Set(), now, now);

        data.applyPlacement(TransactionType.Side.SELL, sellPrice, unclaimedVolume);

        var claimedBuy = matchedBuy.withClaim(matchedBuy.unclaimedFilled());

        var withFlip = Stream.concat(
                current.stream().map(order -> order.id() == matchedBuy.id() ? claimedBuy : order),
                Stream.of(newSell)).toList();

        var reindexed = reindexActive(withFlip);
        var placedSell = reindexed.stream().filter(o -> o.id() == newSell.id()).findFirst().orElse(newSell);

        TrackedOrdersStorage.INSTANCE.set(reindexed);
        TrackedOrdersStorage.persist();

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.OrderFlipped(now)));

        PlayerActionUtil.notifyAll("Flip: buy " + unclaimedVolume + "x @ " + buyPrice + " → " + describeOrder(placedSell), NotificationType.BAZAARDATA);
    }

    // ── Write: OrdersScreen ───────────────────────────────────────────────────

    public static void reconcileOrdersScreen(Map<String, List<OrdersScreenParser.ParsedOrder>> screen, long observedAt) {
        TrackedOrdersStorage.INSTANCE.get().stream()
                .map(TrackedPlayerOrder::productId)
                .distinct()
                .filter(id -> !screen.containsKey(id))
                .forEach(id -> {
                    PlayerActionUtil.notifyAll("Screen evict (absent): " + id, NotificationType.BAZAARDATA);
                    reconcileProduct(id, List.of(), observedAt);
                });

        screen.forEach((productId, entries) -> reconcileProduct(productId, entries, observedAt));
    }

    private static void reconcileProduct(String productId, List<OrdersScreenParser.ParsedOrder> entries, long observedAt) {
        long now = System.currentTimeMillis();

        var current = TrackedOrdersStorage.INSTANCE.get();
        var unrelated = current.stream().filter(order -> !order.productId().equals(productId)).toList();
        var forProduct = current.stream().filter(order ->  order.productId().equals(productId)).toList();

        record MatchKey(TransactionType.Side side, double price, int totalAmount) {}

        var byKey = forProduct.stream()
                .collect(Collectors.groupingBy(
                        order -> new MatchKey(order.side(), order.price(), order.originalAmount()),
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayDeque::new)));

        var matched = new HashSet<Long>();

        var reconciled = entries.stream()
                .map(entry -> {
                    var key = new MatchKey(entry.side(), entry.price(), entry.totalAmount());
                    var candidates = byKey.getOrDefault(key, new ArrayDeque<>());

                    TrackedPlayerOrder found = candidates.stream()
                            .filter(order -> !matched.contains(order.id()))
                            .filter(order -> order.lastKnownIndex() == entry.index())
                            .findFirst()
                            .orElse(null);

                    if (found == null) {
                        double tol = Math.round((0.9 / entry.totalAmount()) * 10) / 10.0 + entry.price() * 0.01;

                        found = forProduct.stream()
                                .filter(order -> !matched.contains(order.id()))
                                .filter(order -> order.side() == entry.side())
                                .filter(order -> order.originalAmount() == entry.totalAmount())
                                .filter(order -> Math.abs(order.price() - entry.price()) <= tol)
                                .findFirst()
                                .orElse(null);
                    }

                    TrackedPlayerOrder result;

                    if (found != null) {
                        matched.add(found.id());

                        // Screen is authoritative for position and existence only.
                        // Terminal states are never downgraded by a screen observation.
                        // For active orders, use screen fill as a floor — catch fills
                        // we missed — but never overwrite state that chat already set.
                        int reconciledFill;
                        OrderState reconciledState;

                        if (found.state() instanceof OrderState.Filled
                                || found.state() instanceof OrderState.Claimed
                                || found.state() instanceof OrderState.Cancelled) {
                            reconciledFill  = found.filledAmount();
                            reconciledState = found.state();
                        } else {
                            reconciledFill = Math.max(found.filledAmount(), entry.filledAmount());

                            if (reconciledFill >= entry.totalAmount()) {
                                reconciledState = found.filledAmount() >= entry.totalAmount()
                                        ? found.state()
                                        : new OrderState.Filled(reconciledFill, now);
                            } else if (reconciledFill > 0) {
                                reconciledState = (found.state() instanceof OrderState.Partial
                                        && reconciledFill == found.filledAmount())
                                        ? found.state()
                                        : new OrderState.Partial(reconciledFill);
                            } else {
                                reconciledState = found.state();
                            }
                        }

                        result = new TrackedPlayerOrder(
                                found.id(), productId, entry.side(),
                                entry.price(), entry.totalAmount(),
                                reconciledFill, found.claimedAmount(),
                                entry.index(), reconciledState,
                                found.placedAt(), now);
                    } else {
                        result = new TrackedPlayerOrder(
                                TrackedOrdersStorage.nextId(), productId, entry.side(),
                                entry.price(), entry.totalAmount(),
                                entry.filledAmount(), 0, entry.index(),
                                entry.filledAmount() >= entry.totalAmount()
                                        ? new OrderState.Filled(entry.filledAmount(), now)
                                        : entry.filledAmount() > 0
                                        ? new OrderState.Partial(entry.filledAmount())
                                        : new OrderState.Set(),
                                now, now);
                    }

                    PlayerActionUtil.notifyAll("Screen anchor: " + describeOrder(result) + " | index=" + result.lastKnownIndex(), NotificationType.BAZAARDATA);
                    return result;
                })
                .toList();

        var preserved = forProduct.stream()
                .filter(order -> !matched.contains(order.id()))
                .filter(order -> order.lastUpdatedAt() > observedAt)
                .peek(order -> PlayerActionUtil.notifyAll("Screen evict skipped: " + describeOrder(order) + " | lastUpdatedAt=" + order.lastUpdatedAt() + " > observedAt=" + observedAt, NotificationType.BAZAARDATA))
                .toList();

        forProduct.stream()
                .filter(order -> !matched.contains(order.id()))
                .filter(order -> order.lastUpdatedAt() <= observedAt)
                .forEach(order -> PlayerActionUtil.notifyAll("Screen evict: " + describeOrder(order) + " | not on screen, lastUpdatedAt=" + order.lastUpdatedAt(), NotificationType.BAZAARDATA));

        TrackedOrdersStorage.INSTANCE.set(Stream.concat(unrelated.stream(), Stream.concat(reconciled.stream(), preserved.stream())).toList());
        TrackedOrdersStorage.persist();

        EVENT_BUS.post(new BazaarPricePoolUpdateEvent(productId, new DataSource.OrdersScreen(now)));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public static OptionalDouble findItemPriceOptional(String productId, TransactionType transaction) {
        if (productId == null) return OptionalDouble.empty();

        var data = REGISTRY.get(productId);
        if (data == null) return OptionalDouble.empty();

        var entry = data.bookFor(transaction).firstEntry();

        return entry != null
                ? OptionalDouble.of(entry.getValue().price())
                : OptionalDouble.empty();
    }

    public static OptionalInt getOrderCountOptional(String productId, TransactionType transaction, double price) {
        if (productId == null) return OptionalInt.empty();

        var data = REGISTRY.get(productId);
        if (data == null) return OptionalInt.empty();

        var pool = data.bookFor(transaction).get(price);

        return pool != null
                ? OptionalInt.of(pool.aggregatedOrderCount())
                : OptionalInt.of(0);
    }

    public static OptionalInt getTotalVolumeOptional(String productId, TransactionType transaction, double price) {
        if (productId == null) return OptionalInt.empty();

        var data = REGISTRY.get(productId);
        if (data == null) return OptionalInt.empty();

        var pool = data.bookFor(transaction).get(price);

        return pool != null
                ? OptionalInt.of((int) pool.totalVolume())
                : OptionalInt.empty();
    }

    public static List<TrackedPlayerOrder> getTrackedOrders(String productId) {
        return TrackedOrdersStorage.INSTANCE.get().stream()
                .filter(order -> order.productId().equals(productId))
                .toList();
    }

    public static int bookPositionOf(String productId, TransactionType.Side side, double price) {
        var data = REGISTRY.get(productId);

        return data == null ? -1 : data.bookPositionOf(side, price);
    }

    public static Optional<String> findProductIdOptional(String naturalName) {
        if (naturalName == null || naturalName.isBlank()) return Optional.empty();

        ensureConversionsLoaded();

        return Optional.ofNullable(nameToProductIdCache.get(naturalName.toLowerCase(Locale.ROOT)));
    }

    public static String describeOrder(TrackedPlayerOrder o) {
        String header = "%s Order: %dx @ %.1f coins".formatted(o.side().getString(), o.originalAmount(), o.price());

        String status = switch (o.state()) {
            case OrderState.Set ignored -> "Waiting for fills...";
            case OrderState.Partial partial -> "Filled %d / %d".formatted(partial.filledSoFar(), o.originalAmount());
            case OrderState.Filled filled -> "Complete — %d filled".formatted(filled.totalFilled());
            case OrderState.Cancelled ignored -> "Cancelled";
            case OrderState.Claimed claim -> "Claimed %d items".formatted(claim.claimedAmount());
        };

        return header + " | " + status;
    }

    public static String describeSource(DataSource source) {
        return switch (source) {
            case DataSource.ApiSnapshot s -> "API snapshot @ " + s.snapshotTs();
            case DataSource.ItemSummary s -> "Item Summary @ " + s.observedAt();
            case DataSource.ManageOrders s -> "Manage Orders @ " + s.observedAt();
            case DataSource.OrdersScreen s -> "Orders screen @ " + s.observedAt();
            case DataSource.OrderFlipped s -> "Order flipped → sell confirmed @ " + s.confirmedAt();
            case DataSource.OrderPlaced s -> "Order placed @ " + s.placedAt();
            case DataSource.OrderFilled s -> "Order filled @ " + s.confirmedAt();
            case DataSource.OrderCancelled s -> "Order cancelled @ " + s.confirmedAt();
            case DataSource.InstantDeal s -> "Instant deal (" + s.side().getString() + ") @ " + s.confirmedAt();
        };
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void replaceById(long id, TrackedPlayerOrder updated) {
        TrackedOrdersStorage.INSTANCE.set(TrackedOrdersStorage.INSTANCE.get().stream()
                .map(order -> order.id() == id ? updated : order)
                .toList());

        TrackedOrdersStorage.persist();
    }

    /**
     * Cancels a matched order and fully reindexes all remaining active orders.
     * A SELL cancel shifts all BUY sideOffsets — full reindex required.
     * Pool decrement and event firing are the responsibility of the call site.
     */
    private static void cancelAndReindex(TrackedPlayerOrder matched) {
        var afterCancel = TrackedOrdersStorage.INSTANCE.get().stream()
                .map(order -> order.id() == matched.id() ? order.cancelled() : order)
                .toList();
        TrackedOrdersStorage.INSTANCE.set(reindexActive(afterCancel));
        TrackedOrdersStorage.persist();
    }

    /**
     * Recomputes the exact screen slot index ({@code lastKnownIndex}) for every active
     * (non-cancelled, non-claimed) order in the list.
     *
     * <p>Screen layout:
     * <ul>
     *   <li>Row 0 (slots 0-8): top frame glass, always present.</li>
     *   <li>Content rows (slots 9, 18, 27...): left glass | 7 order slots | right glass.</li>
     *   <li>SELL offers always occupy at least 1 content row; BUY orders follow.</li>
     * </ul>
     *
     * <p>Logical position {@code n} maps to screen slot {@code 10 + (n/7)*9 + (n%7)}.
     */
    private static List<TrackedPlayerOrder> reindexActive(List<TrackedPlayerOrder> orders) {
        var active = orders.stream()
                .filter(o -> !(o.state() instanceof OrderState.Cancelled))
                .filter(o -> !(o.state() instanceof OrderState.Claimed))
                .toList();

        return orders.stream()
                .map(order -> {
                    if (order.state() instanceof OrderState.Cancelled || order.state() instanceof OrderState.Claimed) return order;
                    var others = active.stream().filter(o -> o.id() != order.id()).toList();
                    return order.reanchored(computeScreenSlot(
                            order.productId(), order.side(), order.price(),
                            order.placedAt(), order.state() instanceof OrderState.Filled, others));
                })
                .toList();
    }

    private static int computeScreenSlot(
            String productId, TransactionType.Side side, double price,
            long placedAt, boolean isFilled, List<TrackedPlayerOrder> activeOthers) {

        var sameSideOthers = activeOthers.stream()
                .filter(o -> o.side() == side)
                .toList();

        int sideOffset;
        if (side == TransactionType.Side.BUY) {
            int activeSellCount = (int) activeOthers.stream()
                    .filter(o -> o.side() == TransactionType.Side.SELL)
                    .count();
            int sellRows = Math.max(1, (int) Math.ceil(activeSellCount / 7.0));
            sideOffset = sellRows * 7;
        } else {
            sideOffset = 0;
        }

        int interGroupOffset = (int) sameSideOthers.stream()
                .filter(order -> !order.productId().equals(productId))
                .filter(order -> order.productId().compareTo(productId) < 0)
                .count();

        var sameGroupOthers = sameSideOthers.stream()
                .filter(order -> order.productId().equals(productId))
                .toList();

        int intraGroupPos;

        if (isFilled) {
            intraGroupPos = (int) sameGroupOthers.stream()
                    .filter(order -> order.state() instanceof OrderState.Filled)
                    .filter(order -> order.placedAt() < placedAt)
                    .count();
        } else {
            int filledAhead = (int) sameGroupOthers.stream()
                    .filter(order -> order.state() instanceof OrderState.Filled)
                    .count();
            int higherPriceAhead = (int) sameGroupOthers.stream()
                    .filter(order -> !(order.state() instanceof OrderState.Filled))
                    .filter(order -> order.price() > price)
                    .count();
            int samePriceOlderAhead = (int) sameGroupOthers.stream()
                    .filter(order -> !(order.state() instanceof OrderState.Filled))
                    .filter(order -> Double.compare(order.price(), price) == 0)
                    .filter(order -> order.placedAt() < placedAt)
                    .count();
            intraGroupPos = filledAhead + higherPriceAhead + samePriceOlderAhead;
        }

        int logicalPos = sideOffset + interGroupOffset + intraGroupPos;

        return 10 + (logicalPos / 7) * 9 + (logicalPos % 7);
    }

    private static Set<Double> priceSet(List<PriceLevelPool> levels) {
        return levels.stream()
                .map(PriceLevelPool::price)
                .collect(Collectors.toSet());
    }

    private static Map<String, Map<TransactionType.Side, Set<Double>>> buildActiveTrackedIndex() {
        var index = new HashMap<String, Map<TransactionType.Side, Set<Double>>>();
        TrackedOrdersStorage.INSTANCE.get().stream()
                .filter(order -> order.state() instanceof OrderState.Set
                        || order.state() instanceof OrderState.Partial)
                .forEach(order ->
                        index.computeIfAbsent(order.productId(), k -> new HashMap<>())
                                .computeIfAbsent(order.side(), k -> new HashSet<>())
                                .add(order.price()));
        return index;
    }
    private static void ensureConversionsLoaded() {
        if (conversionsLoaded) return;

        synchronized (BazaarProductRegistry.class) {
            if (conversionsLoaded) return;

            try {
                var mutable = new HashMap<String, String>();
                var conversions = ResourceManager.getResourceJson().getAsJsonObject();

                conversions.keySet().stream()
                        .filter(key -> conversions.get(key).getAsString() != null)
                        .forEach(key -> mutable.put(conversions.get(key).getAsString().toLowerCase(Locale.ROOT), key));

                nameToProductIdCache = Collections.unmodifiableMap(mutable);
            } catch (Exception e) {
                Util.notifyError("Failed loading bazaarConversions cache", e);
                nameToProductIdCache = Map.of();
            } finally {
                conversionsLoaded = true;
            }
        }
    }
}
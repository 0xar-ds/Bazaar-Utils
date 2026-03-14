package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.events.BazaarPricePoolBatchUpdateEvent;
import com.github.mkram17.bazaarutils.events.BazaarPricePoolUpdateEvent;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.ScreenChangeEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RegisterWidget;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderState;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TrackedPlayerOrder;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.OrderBookPanelWidget;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.OrderBookPanelWidget.DataRow;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets.WidgetManager;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

@Module
public class ItemPriceSourcesOverlay extends BUListener {

    public boolean isEnabled() { return true; }

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_OFFSET = 4;
    private static final int BOOK_ROWS = 8;

    private static String  selectedProductId = null;
    private static boolean searchMode = false;
    private static String  searchQuery = "";

    private static OrderBookPanelWidget PANEL = null;

    public ItemPriceSourcesOverlay() {}

    public static void onKeyPressed(int keyCode) {
        if (!searchMode) return;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchMode  = false;
            searchQuery = "";
            rebuild();
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            rebuild();
        }
    }

    public static void onCharTyped(char chr) {
        if (!searchMode) return;
        if (Character.isLetterOrDigit(chr) || chr == '_' || chr == ' ') {
            searchQuery += chr;
            rebuild();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onScreenChange(ScreenChangeEvent event) { rebuild(); }

    @EventHandler
    private void onChestLoaded(ChestLoadedEvent event) { rebuild(); }

    @EventHandler
    private void onPoolUpdate(BazaarPricePoolUpdateEvent event) {
        if (event.productId().equals(selectedProductId)) rebuild();
    }

    @EventHandler
    private void onPoolBatchUpdate(BazaarPricePoolBatchUpdateEvent event) {
        if (event.affects(selectedProductId)) rebuild();
    }

    @RegisterWidget
    public static List<OrderBookPanelWidget> getWidget() {
        if (!BazaarUtilsModules.ItemPriceSourcesOverlay.isEnabled()) return Collections.emptyList();
        allocateIfNeeded();
        return PANEL == null ? Collections.emptyList() : List.of(PANEL);
    }

    private static boolean isContainerOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>;
    }

    private static void allocateIfNeeded() {
        if (PANEL != null) return;
        PANEL = new OrderBookPanelWidget(0, 0, PANEL_WIDTH, BOOK_ROWS);
        PANEL.visible = false;
    }

    private static void rebuild() {
        if (!BazaarUtilsModules.ItemPriceSourcesOverlay.isEnabled() || !isContainerOpen()) {
            if (PANEL != null) PANEL.visible = false;
            return;
        }

        allocateIfNeeded();

        var dims = WidgetManager.getScreenDimensions(BazaarScreens.ALL.toArray(ScreenType[]::new));
        if (dims.isEmpty()) {
            PANEL.visible = false;
            return;
        }

        PANEL.reposition(dims.get().x() + dims.get().backgroundWidth() + PANEL_OFFSET, dims.get().y());

        if (selectedProductId == null) {
            var keys = BazaarProductRegistry.getAllProductIds();
            selectedProductId = keys.isEmpty() ? null : keys.iterator().next();
        }

        if (searchMode) {
            rebuildSearch();
        } else {
            rebuildNormal();
        }

        PANEL.visible = true;
    }

    private static void rebuildSearch() {
        String cursor = (System.currentTimeMillis() / 500 % 2 == 0) ? "█" : " ";
        PANEL.setTitle("Search: " + searchQuery + cursor);
        PANEL.setOnTitleClick(null);
        PANEL.setHeaders("Results", "");

        List<String> results = filteredResults();
        var buyRows = new ArrayList<DataRow>();
        var sellRows = new ArrayList<DataRow>();

        for (int i = 0; i < BOOK_ROWS; i++) {
            if (i < results.size()) {
                String id = results.get(i);
                boolean sel = id.equals(selectedProductId);
                Text label = sel
                        ? Text.literal("◆ ").withColor(0xFF00CCCC)
                        .append(Text.literal(id.replace('_', ' ')).formatted(Formatting.WHITE))
                        : Text.literal("  ")
                        .append(Text.literal(id.replace('_', ' ')).formatted(Formatting.GRAY));
                buyRows.add(new DataRow(label, Text.empty(), () -> selectProduct(id)));
            } else {
                buyRows.add(new DataRow(Text.empty(), Text.empty()));
            }
            sellRows.add(new DataRow(Text.empty(), Text.empty()));
        }

        PANEL.setBuyRows(buyRows);
        PANEL.setSellRows(sellRows);
    }

    private static void rebuildNormal() {
        String display = selectedProductId == null
                ? "no products"
                : selectedProductId.replace('_', ' ').toUpperCase();

        PANEL.setTitle(display);
        PANEL.setOnTitleClick(btn -> enterSearchMode());
        PANEL.setHeaders("Buy Orders", "Sell Offers");

        if (selectedProductId == null) {
            PANEL.setBuyRows(List.of(new DataRow(Text.literal("—").formatted(Formatting.DARK_GRAY))));
            PANEL.setSellRows(List.of(new DataRow(Text.literal("—").formatted(Formatting.DARK_GRAY))));
            return;
        }

        var buyTx = new TransactionType(TransactionType.Side.BUY,  TransactionType.Method.ORDER);
        var sellTx = new TransactionType(TransactionType.Side.SELL, TransactionType.Method.ORDER);

        var trackedBuy = trackedByPrice(selectedProductId, TransactionType.Side.BUY);
        var trackedSell = trackedByPrice(selectedProductId, TransactionType.Side.SELL);

        PANEL.setBuyRows(bookLevelRows(selectedProductId, buyTx,  trackedBuy,  BOOK_ROWS));
        PANEL.setSellRows(bookLevelRows(selectedProductId, sellTx, trackedSell, BOOK_ROWS));
    }

    private static void enterSearchMode() {
        searchMode = true;
        searchQuery = "";
        rebuild();
    }

    private static void selectProduct(String productId) {
        selectedProductId = productId;
        searchMode = false;
        searchQuery = "";
        rebuild();
    }

    private static List<String> filteredResults() {
        String q = searchQuery.toLowerCase(Locale.ROOT).replace(' ', '_');
        var results = new ArrayList<String>();
        for (String id : BazaarProductRegistry.getAllProductIds()) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)) {
                results.add(id);
                if (results.size() == BOOK_ROWS) break;
            }
        }
        Collections.sort(results);
        return results;
    }

    private static Map<Double, List<TrackedPlayerOrder>> trackedByPrice(String productId, TransactionType.Side side) {
        return BazaarProductRegistry.getTrackedOrders(productId).stream()
                .filter(order -> order.side() == side)
                .filter(order -> !(order.state() instanceof OrderState.Cancelled))
                .collect(Collectors.groupingBy(TrackedPlayerOrder::price));
    }

    private static void emitBuckets(List<DataRow> rows, int limit, double price, long poolVol, int poolOrders, List<TrackedPlayerOrder> ours) {
        List<TrackedPlayerOrder> filled = ours.stream().filter(order -> order.state() instanceof OrderState.Filled).toList();
        List<TrackedPlayerOrder> active = ours.stream().filter(order -> order.state() instanceof OrderState.Set || order.state() instanceof OrderState.Partial).toList();
        List<TrackedPlayerOrder> claimed = ours.stream().filter(order -> order.state() instanceof OrderState.Claimed).toList();

        if (!filled.isEmpty()  && rows.size() < limit) rows.add(buildRow(price, poolVol, poolOrders, filled));
        if (!active.isEmpty()  && rows.size() < limit) rows.add(buildRow(price, poolVol, poolOrders, active));
        if (!claimed.isEmpty() && rows.size() < limit) rows.add(buildRow(price, poolVol, poolOrders, claimed));
    }

    private static List<DataRow> bookLevelRows(String productId, TransactionType tx, Map<Double, List<TrackedPlayerOrder>> trackedByPrice, int limit) {
        var rows = new ArrayList<DataRow>();
        var book = BazaarProductRegistry.getOrCreate(productId).bookFor(tx);

        // Pass 1: price levels with at least one filled order (pinned to top)
        for (var priceEntry : trackedByPrice.entrySet()) {
            if (rows.size() >= limit) break;
            boolean anyFilled = priceEntry.getValue().stream().anyMatch(o -> o.state() instanceof OrderState.Filled);
            if (!anyFilled) continue;

            double price = priceEntry.getKey();
            var poolEntry = book.get(price);
            long poolVol = poolEntry != null ? poolEntry.totalVolume() : 0;
            int poolOrders = poolEntry != null ? poolEntry.aggregatedOrderCount() : 0;
            emitBuckets(rows, limit, price, poolVol, poolOrders, priceEntry.getValue());
        }

        // Pass 2: pool rows (skip prices already handled in pass 1)
        for (var entry : book.entrySet()) {
            if (rows.size() >= limit) break;
            double price = entry.getValue().price();
            var ours = trackedByPrice.get(price);

            if (ours != null && ours.stream().anyMatch(o -> o.state() instanceof OrderState.Filled)) continue;

            if (ours != null) {
                emitBuckets(rows, limit, price, entry.getValue().totalVolume(), entry.getValue().aggregatedOrderCount(), ours);
            } else {
                rows.add(buildRow(price, entry.getValue().totalVolume(), entry.getValue().aggregatedOrderCount(), null));
            }
        }

        // Pass 3: tracked orders at prices no longer in the pool
        for (var priceEntry : trackedByPrice.entrySet()) {
            if (rows.size() >= limit) break;
            double price = priceEntry.getKey();
            if (book.containsKey(price)) continue;
            boolean anyFilled = priceEntry.getValue().stream().anyMatch(o -> o.state() instanceof OrderState.Filled);
            if (anyFilled) continue; // already at top
            emitBuckets(rows, limit, price, 0, 0, priceEntry.getValue());
        }

        while (rows.size() < limit) rows.add(new DataRow(Text.empty(), Text.empty()));
        return rows;
    }

    private static DataRow buildRow(double price, long poolVol, int poolOrders, List<TrackedPlayerOrder> ours) {
        boolean hasActive  = ours != null && ours.stream().anyMatch(order -> order.state() instanceof OrderState.Set || order.state() instanceof OrderState.Partial);
        boolean hasFilled  = ours != null && ours.stream().anyMatch(order -> order.state() instanceof OrderState.Filled);
        boolean hasClaimed = ours != null && ours.stream().anyMatch(order -> order.state() instanceof OrderState.Claimed);

        int ourActiveVol = ours == null ? 0 : ours.stream()
                .filter(order -> order.state() instanceof OrderState.Set || order.state() instanceof OrderState.Partial)
                .mapToInt(TrackedPlayerOrder::remainingAmount)
                .sum();
        int ourFilledVol = ours == null ? 0 : ours.stream()
                .filter(order -> order.state() instanceof OrderState.Filled)
                .mapToInt(order -> order.filledAmount() - order.claimedAmount())
                .sum();

        Text badge;
        if (hasFilled) {
            badge = Text.literal("★ ").withColor(0xFFFFAA00);
        } else if (hasClaimed) {
            badge = Text.literal("✔ ").withColor(0xFF555555);
        } else if (hasActive) {
            Formatting posColor = poolOrders <= 1 ? Formatting.GREEN
                    : poolOrders <= 3 ? Formatting.YELLOW
                    : Formatting.RED;
            badge = Text.literal("◆ ").formatted(posColor);
        } else {
            badge = Text.literal("  ");
        }

        int priceColor = hasFilled  ? 0xFFFFAA00
                : hasActive ? 0xFFFFFFFF
                : hasClaimed ? 0xFF555555
                : 0xFFAAAAAA;

        Text left = badge.copy().append(Text.literal(formatCoins(price)).withColor(priceColor));

        Text right;
        if (poolVol > 0) {
            if (ourActiveVol > 0) {
                right = Text.literal(formatAmount((int) poolVol) + "x").withColor(0xFF888888)
                        .append(Text.literal(" / ").withColor(0xFF444444))
                        .append(Text.literal(formatAmount(ourActiveVol) + "x").withColor(0xFF00CCCC));
            } else if (ourFilledVol > 0) {
                // Our portion already decremented from pool — pool shows others only.
                right = Text.literal(formatAmount((int) poolVol) + "x").withColor(0xFF888888).append(Text.literal(" ★").withColor(0xFFFFAA00));
            } else if (hasClaimed) {
                right = Text.literal(formatAmount((int) poolVol) + "x").withColor(0xFF444444).append(Text.literal(" ✔").withColor(0xFF555555));
            } else {
                right = Text.literal(formatAmount((int) poolVol) + "x").withColor(0xFF888888);
            }
        } else if (ourFilledVol > 0) {
            right = Text.literal("filled " + formatAmount(ourFilledVol) + "x").withColor(0xFFFFAA00);
        } else {
            right = Text.empty();
        }

        return new DataRow(left, right);
    }

    private static String formatCoins(double v) {
        if (v >= 1_000_000) return "%.2fM".formatted(v / 1_000_000);
        if (v >= 1_000)     return "%.1fK".formatted(v / 1_000);
        return "%.1f".formatted(v);
    }

    private static String formatAmount(int v) {
        if (v >= 1_000_000) return "%dM".formatted(v / 1_000_000);
        if (v >= 1_000)     return "%dK".formatted(v / 1_000);
        return "%d".formatted(v);
    }
}
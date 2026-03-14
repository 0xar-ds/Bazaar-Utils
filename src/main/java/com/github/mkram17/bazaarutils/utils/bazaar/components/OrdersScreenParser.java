package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class OrdersScreenParser {
    private static final Pattern AMOUNT_LINE = Pattern.compile("(?:Order|Offer) amount: (?<amount>[\\d,]+)x");
    private static final Pattern FILLED_LINE = Pattern.compile("Filled: (?<filledAmount>[\\d,.kKmM]+)/(?<totalAmount>[\\d,.kKmM])+");
    private static final Pattern PRICE_LINE = Pattern.compile("Price per unit: (?<price>[\\d,.]+) coins");

    private static final int MAX_ORDER_SIZE = 71_680;

    public record ParsedOrder(double price, int totalAmount, int filledAmount, TransactionType.Side side, int index) {}

    public record ParseResult(Map<String, List<ParsedOrder>> orders, long observedAt) {}

    private OrdersScreenParser() {}

    /**
     * Parses an open Orders screen into a {@link ParseResult} keyed by productId,
     * ready to be fed into {@link BazaarProductRegistry#reconcileOrdersScreen}.
     */
    public static ParseResult parse(GenericContainerScreen screen) {
        long observedAt = System.currentTimeMillis();
        var handler = screen.getScreenHandler();
        int containerSize = handler.getRows() * 9;
        var slots = handler.slots;

        var orders = IntStream.range(0, containerSize)
                .mapToObj(i -> {
                    var stack = slots.get(i).getStack();
                    if (isFrame(stack)) return null;
                    var tagged = parseEntry(stack, i);
                    if (tagged == null) {
                        PlayerActionUtil.notifyAll("Slot#" + i + " → SKIPPED ('" + stack.getName().getString() + "')", NotificationType.BAZAARDATA);
                    } else {
                        var order = tagged.order();
                        PlayerActionUtil.notifyAll(
                                "Slot#" + i + " → "
                                        + tagged.productId()
                                        + " | " + order.side().getString()
                                        + " " + order.totalAmount() + "x @ " + order.price()
                                        + " | filled=" + order.filledAmount(),
                                NotificationType.BAZAARDATA);
                    }
                    return tagged;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        Tagged::productId,
                        LinkedHashMap::new,
                        Collectors.mapping(Tagged::order, Collectors.toList())));

        return new ParseResult(orders, observedAt);
    }

    private static Tagged parseEntry(ItemStack stack, int index) {
        var name = stack.getCustomName();

        if (name == null) {
            PlayerActionUtil.notifyAll("Slot#" + index + " → null name", NotificationType.BAZAARDATA);
            return null;
        }

        // Custom name is "BUY Wheat" / "SELL Sunflower" — item name is the non-bold sibling.
        String itemName = name.getSiblings().stream()
                .filter(text -> !text.getStyle().isBold())
                .map(Text::getString)
                .map(String::strip)
                .findFirst()
                .orElse("")
                .strip();

        var productId = BazaarProductRegistry.findProductIdOptional(itemName);

        if (productId.isEmpty()) {
            PlayerActionUtil.notifyAll("Slot#" + index + " → no productId for '" + itemName + "'", NotificationType.BAZAARDATA);
            return null;
        }

        TransactionType.Side side = parseSide(name);
        if (side == null) {
            PlayerActionUtil.notifyAll("Slot#" + index + " → null side | siblings="
                            + name.getSiblings().stream()
                            .map(s -> "[bold=" + s.getStyle().isBold() + " text='" + s.getString() + "']")
                            .toList(),
                    NotificationType.BAZAARDATA);
            return null;
        }

        var lore = stack.getComponents().get(DataComponentTypes.LORE);

        if (lore == null) {
            PlayerActionUtil.notifyAll("Slot#" + index + " → null lore", NotificationType.BAZAARDATA);
            return null;
        }

        String filledStr = null;
        String totalStr = null;
        String priceStr = null;

        for (Text line : lore.lines()) {
            String plain = line.getString();
            Matcher matcher;

            if (totalStr == null && (matcher = AMOUNT_LINE.matcher(plain)).find()) { totalStr = matcher.group("amount"); }
            else if (filledStr == null && (matcher = FILLED_LINE.matcher(plain)).find()) { filledStr = matcher.group("filledAmount"); }
            else if (priceStr == null && (matcher = PRICE_LINE.matcher(plain)).find()) { priceStr = matcher.group("price"); }

            if (totalStr != null && priceStr != null) break; // filled may be absent (Set, unfilled)
        }

        if (totalStr == null || priceStr == null) {
            PlayerActionUtil.notifyAll("Slot#" + index + " → pattern miss | lore=" + lore.lines().stream().map(Text::getString).filter(s -> !s.isBlank()).toList(), NotificationType.BAZAARDATA);
            return null;
        }

        int totalAmount  = Util.parseNumber(Util.removeFormatting(totalStr));

        int filledAmount = filledStr != null
                ? Math.min(Util.parseNumber(Util.removeFormatting(filledStr)), totalAmount)
                : 0;

        return new Tagged(productId.get(), new ParsedOrder(parseCoins(priceStr), totalAmount, filledAmount, side, index));
    }

    /** Frame aesthethic around the rendered orders */
    private static boolean isFrame(ItemStack stack) {
        return stack.isEmpty()
                || stack.isOf(Items.BLACK_STAINED_GLASS_PANE)
                || stack.isOf(Items.ARROW)
                || stack.isOf(Items.HOPPER);
    }

    /** Side is encoded in the bold prefix color of the custom name: green = BUY, gold = SELL. */
    private static TransactionType.Side parseSide(Text name) {
        return name.getSiblings().stream()
                .filter(text -> text.getStyle().isBold())
                .map(Text::getString)
                .map(String::strip)
                .map(type -> switch (type) {
                    case "SELL" -> TransactionType.Side.SELL;
                    case "BUY" -> TransactionType.Side.BUY;
                    default -> null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }


    private static double parseCoins(String raw) {
        return Double.parseDouble(raw.replace(",", "").trim());
    }

    // ── Internal carrier ─────────────────────────────────────────────────────

    private record Tagged(String productId, ParsedOrder order) {}
}
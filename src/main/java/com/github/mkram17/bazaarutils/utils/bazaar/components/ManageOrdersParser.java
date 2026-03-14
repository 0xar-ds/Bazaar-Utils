package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarProductRegistry;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class ManageOrdersParser {
    public record ParsedOrderHint(TransactionType.Side side, int amount, String productId, double price) {}

    public record ParseResult(List<ParsedOrderHint> hints, long observedAt) {}

    private ManageOrdersParser() {}

    public static ParseResult parse(ItemStack book, String contextProductId) {
        long observedAt = System.currentTimeMillis();

        var lore = book.getComponents().get(DataComponentTypes.LORE);

        if (lore == null) return new ParseResult(List.of(), observedAt);

        var hints = new ArrayList<ParsedOrderHint>();

        for (Text line : lore.lines()) {
            var siblings = line.getSiblings();

            if (siblings.size() < 7) continue;

            var hint = parseLine(siblings, contextProductId);

            if (hint != null) {
                hints.add(hint);
                PlayerActionUtil.notifyAll(hint.side().getString() + " " + hint.amount() + "x " + hint.productId() + " @ " + hint.price(), NotificationType.BAZAARDATA);
            }
        }

        return new ParseResult(List.copyOf(hints), observedAt);
    }

    private static ParsedOrderHint parseLine(List<Text> siblings, String contextProductId) {
        var first = siblings.getFirst();

        if (!first.getStyle().isBold()) return null;

        TransactionType.Side side = switch (first.getString().strip()) {
            case "BUY" -> TransactionType.Side.BUY;
            case "SELL" -> TransactionType.Side.SELL;
            default -> null;
        };

        if (side == null) return null;

        String amountStr = siblings.get(1).getString().trim();
        String itemName = siblings.get(3).getString().trim();
        String priceStr = siblings.get(5).getString().trim();

        int amount;
        double price;

        try {
            amount = parseAmount(amountStr);
            price  = Double.parseDouble(priceStr.replace(",", "").trim());
        } catch (NumberFormatException e) {
            PlayerActionUtil.notifyAll("Parse failed: amount='" + amountStr + "' price='" + priceStr + "'", NotificationType.BAZAARDATA);

            return null;
        }

        String productId = contextProductId != null
                ? contextProductId
                : BazaarProductRegistry.findProductIdOptional(itemName).orElse(null);

        if (productId == null) {
            PlayerActionUtil.notifyAll("No productId for '" + itemName + "'", NotificationType.BAZAARDATA);

            return null;
        }

        return new ParsedOrderHint(side, amount, productId, price);
    }

    private static int parseAmount(String raw) {
        String s = raw.replace(",", "").trim();

        if (s.endsWith("k") || s.endsWith("K")) return (int) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000);

        if (s.endsWith("m") || s.endsWith("M")) return (int) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000_000);

        return (int) Double.parseDouble(s);
    }
}
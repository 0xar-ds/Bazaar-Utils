package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OrderBookPanelWidget extends ClickableWidget {
    public record DataRow(Text left, Text right, Runnable onClick) {
        public DataRow(Text left, Text right) {
            this(left, right, null);
        }

        public DataRow(Text left) {
            this(left, Text.empty(), null);
        }
    }

    private static final int PADDING_H = 6;
    private static final int PADDING_V = 2;
    private static final int ROW_HEIGHT = 10;
    private static final int HEADER_HEIGHT = 12;
    private static final int TITLE_HEIGHT = 10;
    private static final int FRAME_INSET = 2;
    private static final int CONTENT_INSET = 3;

    private static final int COLOR_FRAME_BG = 0xFF636363;
    private static final int COLOR_BEVEL_LIGHT = 0xFF8B8B8B;
    private static final int COLOR_BEVEL_SHADOW = 0xFF1E1E1E;
    private static final int COLOR_WELL_BG = 0xFF252525;
    private static final int COLOR_INSET_SHADOW = 0xFF161616;
    private static final int COLOR_INSET_LIGHT = 0xFF424242;
    private static final int COLOR_HEADER_BG = 0xFF383838;
    private static final int COLOR_VDIV_SHADOW = 0xFF161616;
    private static final int COLOR_VDIV_LIGHT = 0xFF424242;
    private static final int COLOR_HAIRLINE = 0xFF161616;
    private static final int COLOR_HOVER = 0x22FFFFFF;
    private static final int COLOR_BUY = 0xFF55FFFF;
    private static final int COLOR_SELL = 0xFFFFFF55;
    private static final int COLOR_TITLE = 0xFFDDDDDD;

    @Setter
    private String title = "";

    private String leftHeader = "";
    private String rightHeader = "";

    @Setter
    private Consumer<Integer> onTitleClick = null;

    private final List<DataRow> buyRows  = new ArrayList<>();
    private final List<DataRow> sellRows = new ArrayList<>();

    private final int panelWidth;
    private final int rows;

    public OrderBookPanelWidget(int x, int y, int panelWidth, int rows) {
        super(x, y, panelWidth, computeHeight(rows), Text.empty());
        this.panelWidth = panelWidth;
        this.rows = rows;
    }

    private static int computeHeight(int rows) {
        return CONTENT_INSET * 2 + TITLE_HEIGHT + 1 + HEADER_HEIGHT + 1 + rows * ROW_HEIGHT + PADDING_V;
    }

    public void setHeaders(String left, String right) {
        leftHeader = left;
        rightHeader = right;
    }

    public void setBuyRows(List<DataRow> r) {
        buyRows.clear();
        buyRows.addAll(r);
    }

    public void setSellRows(List<DataRow> r) {
        sellRows.clear();
        sellRows.addAll(r);
    }

    public void reposition(int x, int y) {
        setX(x); setY(y);
    }


    // ── Render helpers ────────────────────────────────────────────────────────

    private static void drawBevelBox(DrawContext ctx, int x, int y, int w, int h, int fill, int light, int shadow) {
        ctx.fill(x, y, x + w, y + h, fill);
        ctx.fill(x, y, x + w, y + 1, light);
        ctx.fill(x, y, x + 1, y + h, light);
        ctx.fill(x, y + h - 1, x + w, y + h, shadow);
        ctx.fill(x + w - 1, y, x + w,     y + h,     shadow);
    }

    private static void drawInsetBorder(DrawContext ctx, int x, int y, int w, int h, int shadow, int light) {
        ctx.fill(x, y, x + w, y + 1, shadow);
        ctx.fill(x, y, x + 1, y + h, shadow);
        ctx.fill(x, y + h - 1, x + w, y + h, light);
        ctx.fill(x + w - 1, y, x + w, y + h, light);
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        final int x = getX(), y = getY(), w = panelWidth, h = height;
        final TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        drawBevelBox(ctx, x, y, w, h, COLOR_FRAME_BG, COLOR_BEVEL_LIGHT, COLOR_BEVEL_SHADOW);

        final int wx = x + FRAME_INSET, wy = y + FRAME_INSET;
        final int ww = w - FRAME_INSET * 2, wh = h - FRAME_INSET * 2;
        ctx.fill(wx, wy, wx + ww, wy + wh, COLOR_WELL_BG);
        drawInsetBorder(ctx, wx, wy, ww, wh, COLOR_INSET_SHADOW, COLOR_INSET_LIGHT);

        final int cx = wx + 1, cy = wy + 1;
        final int cw = ww - 2;
        final int midX = cx + cw / 2;

        final int titleBottom = cy + TITLE_HEIGHT;

        if (onTitleClick != null
                && mouseX >= cx && mouseX < cx + cw
                && mouseY >= cy && mouseY < titleBottom) {
            ctx.fill(cx, cy, cx + cw, titleBottom, COLOR_HOVER);
        }

        ctx.drawText(tr,
                Text.literal("⌕ ").withColor(0xFF555555).append(Text.literal(title).withColor(COLOR_TITLE)),
                cx + PADDING_H, cy + (TITLE_HEIGHT - tr.fontHeight) / 2,
                0xFFFFFFFF, false);

        ctx.fill(cx, titleBottom, cx + cw, titleBottom + 1, COLOR_HAIRLINE);

        final int headerTop    = titleBottom + 1;
        final int headerBottom = headerTop + HEADER_HEIGHT;
        ctx.fill(cx, headerTop, cx + cw, headerBottom, COLOR_HEADER_BG);
        ctx.fill(cx, headerBottom, cx + cw, headerBottom + 1, COLOR_HAIRLINE);

        final int headerTextY = headerTop + (HEADER_HEIGHT - tr.fontHeight) / 2;
        ctx.drawText(tr, leftHeader, cx + PADDING_H, headerTextY, COLOR_BUY, false);
        ctx.drawText(tr, rightHeader, cx + cw - PADDING_H - tr.getWidth(rightHeader), headerTextY, COLOR_SELL, false);

        final int dataTop = headerBottom + 1;
        final int dataBottom = wy + wh - 1;
        ctx.fill(midX, dataTop, midX + 1, dataBottom, COLOR_VDIV_SHADOW);
        ctx.fill(midX + 1, dataTop, midX + 2, dataBottom, COLOR_VDIV_LIGHT);

        for (int i = 0; i < rows; i++) {
            final int rowY  = dataTop + i * ROW_HEIGHT;
            final int textY = rowY + (ROW_HEIGHT - tr.fontHeight) / 2;

            if (i < buyRows.size()) {
                DataRow row = buyRows.get(i);
                int colEnd = midX;

                if (row.onClick() != null && mouseX >= cx && mouseX < colEnd && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    ctx.fill(cx, rowY, colEnd, rowY + ROW_HEIGHT, COLOR_HOVER);
                }

                ctx.enableScissor(cx, rowY, colEnd, rowY + ROW_HEIGHT);
                if (row.left() != null && !row.left().getString().isEmpty()) {
                    ctx.drawText(tr, row.left(), cx + PADDING_H, textY, 0xFFFFFFFF, false);
                }
                if (row.right() != null && !row.right().getString().isEmpty()) {
                    int rw = tr.getWidth(row.right());
                    ctx.drawText(tr, row.right(), colEnd - PADDING_H - rw, textY, 0xFFFFFFFF, false);
                }
                ctx.disableScissor();
            }

            if (i < sellRows.size()) {
                DataRow row = sellRows.get(i);
                int colStart = midX + 2;
                int colEnd = cx + cw;

                if (row.onClick() != null && mouseX >= colStart && mouseX < colEnd && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    ctx.fill(colStart, rowY, colEnd, rowY + ROW_HEIGHT, COLOR_HOVER);
                }

                ctx.enableScissor(colStart, rowY, colEnd, rowY + ROW_HEIGHT);
                if (row.left() != null && !row.left().getString().isEmpty()) {
                    ctx.drawText(tr, row.left(), colStart + PADDING_H, textY, 0xFFFFFFFF, false);
                }
                if (row.right() != null && !row.right().getString().isEmpty()) {
                    int rw = tr.getWidth(row.right());
                    ctx.drawText(tr, row.right(), colEnd - PADDING_H - rw, textY, 0xFFFFFFFF, false);
                }
                ctx.disableScissor();
            }
        }
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        final int mx = (int) click.x(), my = (int) click.y();
        final int x = getX(), w = panelWidth;
        final int cx = x + CONTENT_INSET, cy = getY() + CONTENT_INSET;
        final int cw = w - CONTENT_INSET * 2;
        final int midX = cx + cw / 2;

        if (onTitleClick != null && mx >= cx && mx < cx + cw && my >= cy && my < cy + TITLE_HEIGHT) {
            onTitleClick.accept(click.buttonInfo().button());
            return;
        }

        final int dataTop = cy + TITLE_HEIGHT + 1 + HEADER_HEIGHT + 1;
        for (int i = 0; i < rows; i++) {
            final int rowY = dataTop + i * ROW_HEIGHT;
            if (my < rowY || my >= rowY + ROW_HEIGHT) continue;

            if (mx >= cx && mx < midX && i < buyRows.size()) {
                var row = buyRows.get(i);
                if (row.onClick() != null) { row.onClick().run(); return; }
            }
            if (mx >= midX + 2 && mx < cx + cw && i < sellRows.size()) {
                var row = sellRows.get(i);
                if (row.onClick() != null) { row.onClick().run(); return; }
            }
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
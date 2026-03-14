package com.github.mkram17.bazaarutils.utils.minecraft.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class TextDisplayWidget extends ClickableWidget {

    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    // Non-final — callers mutate text in-place without re-registering the widget.
    private Text text;
    private final Alignment alignment;

    /**
     * Optional click handler. Receives the mouse button index:
     *   0 = left, 1 = right, 2 = middle.
     * Null means the widget is non-interactive.
     */
    private Consumer<Integer> onClickHandler = null;

    public TextDisplayWidget(int x, int y, int width, int height, Text text, Alignment alignment) {
        super(x, y, width, height, text);
        this.text = text;
        this.alignment = alignment;
    }

    public TextDisplayWidget(int x, int y, int width, int height, Text text) {
        this(x, y, width, height, text, Alignment.LEFT);
    }

    /** Update displayed text without re-registering the widget with the screen. */
    public void setText(Text text) {
        this.text = text;
    }

    /**
     * Attach a click handler. Pass {@code null} to remove it.
     * The handler receives the mouse button index (0=left, 1=right, 2=middle).
     */
    public void setOnClick(Consumer<Integer> handler) {
        this.onClickHandler = handler;
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        if (onClickHandler != null) onClickHandler.accept(click.buttonInfo().button());
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textY = this.getY() + (this.height - textRenderer.fontHeight) / 2;
        int textX = switch (alignment) {
            case LEFT   -> this.getX();
            case CENTER -> this.getX() + (this.width - textRenderer.getWidth(text)) / 2;
            case RIGHT  -> this.getX() + this.width - textRenderer.getWidth(text);
        };
        context.drawText(textRenderer, text, textX, textY, 0xFFFFFFFF, false);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.LastVisitedPages;
import com.github.mkram17.bazaarutils.features.gui.inventory.OrderStatusHighlight;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.misc.SlotHighlightCache;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Atlases;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

//used for SlotClickEvent, register keybinds in chests, block slot clicks, highlighting slots
@Mixin(value = HandledScreen.class, priority = 999)
public abstract class MixinHandledScreen extends Screen {
	protected MixinHandledScreen(Text title) {
		super(title);
	}

	@Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
	private void onHandleMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
		if (slot == null) {
            return;
        }

		HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
		SlotClickEvent event = new SlotClickEvent(screen, slot, slotId, button, actionType);
		BazaarUtils.EVENT_BUS.post(event);
		// Use the accessor to safely get the client instance
		MinecraftClient client = ((AccessorScreen) screen).getClient();

		if (event.isCancelled()) {
			ci.cancel();
			return;
		}

		if (event.usePickblockInstead) {
			assert client != null && client.player != null && client.interactionManager != null;
            client.interactionManager.clickSlot(
					screen.getScreenHandler().syncId,
					slotId,
					2,
					SlotActionType.PICKUP,
					client.player
			);
			ci.cancel();
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void addConfiguredButtons(CallbackInfo ci) {
		for (ClickableWidget button : ConfigUtil.getWidgets()) {
			this.addDrawableChild(button);
		}
	}

	@Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
	private void drawOnItem_OrderStatusHighlight(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (slot == null || !slot.hasStack() || !ScreenManager.getInstance().isCurrent(BazaarScreens.ORDERS_PAGE)) return;

		if (MinecraftClient.getInstance().player != null && slot.inventory == MinecraftClient.getInstance().player.getInventory()) return;

		if (BazaarUtilsModules.OrderStatusHighlight.isEnabled() && SlotHighlightCache.orderStatusHighlightCache.containsKey(slot.getIndex())) {
			draw(context, slot.x, slot.y, SlotHighlightCache.orderStatusHighlightCache.get(slot.getIndex()));
		}
	}


	@Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
	private void drawOnItem_InstaSellHighlight(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (slot == null || !slot.hasStack() || !ScreenManager.getInstance().isCurrent(BazaarScreens.MAIN_PAGE)) return;

		if (MinecraftClient.getInstance().player == null || !(slot.inventory == MinecraftClient.getInstance().player.getInventory())) return;

		if (BazaarUtilsModules.InstantSellHighlight.isEnabled() && SlotHighlightCache.instaSellHighlightCache.containsKey(slot.getIndex())) {
			draw(context, slot.x, slot.y, SlotHighlightCache.instaSellHighlightCache.get(slot.getIndex()));
		}
	}

	@Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
	private void drawOnItem_LastVisitedPages(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (slot == null || !slot.hasStack()) return;
		if (!BazaarUtilsModules.LastVisitedPages.isSubmapActive()) return;
		if (MinecraftClient.getInstance().player == null
				|| !(slot.inventory == MinecraftClient.getInstance().player.getInventory())) return;

		List<OrderInfo> pages = LastVisitedPages.pages();
		BazaarDataManager.findProductIdOptional(slot.getStack().getName().getString())
				.flatMap(productId -> {
					for (int i = 0; i < pages.size(); i++) {
						if (productId.equals(pages.get(i).getProductID())) return Optional.of(i);
					}
					return Optional.empty();
				})
				.ifPresent(index -> {
					String label = (index == 9) ? "0" : String.valueOf(index + 1);
 					context.fill(slot.x, slot.y, slot.x + 7, slot.y + 7, 0xBB000000);
					context.drawText(textRenderer, label, slot.x + 1, slot.y + 1, 0xFFFFE040, true);
				});
	}

	@Unique
	protected void draw(DrawContext context, int x, int y, int argb) {
		final var sprite = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Atlases.GUI)
				.getSprite(OrderStatusHighlight.IDENTIFIER);

		context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED,
				sprite, x, y, 16, 16, argb
		);
	}

}
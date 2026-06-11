package com.sighs.universalarmory.gui;

import com.sighs.universalarmory.armory.ArmoryGunQueries;
import com.sighs.universalarmory.armory.ArmoryInstance;
import com.sighs.universalarmory.armory.ArmoryManager;
import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import com.sighs.universalarmory.client.render.FirstPersonStyleGunPreviewRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public class ArmoryWorkScreen extends Screen {

    private static final Component TITLE = Component.translatable("screen.universalarmory.armory_work");
    private static final int[] ROW_LAYOUT = {6, 6, 5};
    private static final int DISPLAY_GUN_COUNT = 17;

    private final List<ItemStack> displayGuns = new ArrayList<>();
    private final FirstPersonStyleGunPreviewRenderer previewRenderer = FirstPersonStyleGunPreviewRenderer.getInstance();

    public ArmoryWorkScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        super.init();
        displayGuns.clear();

        Player player = minecraft != null ? minecraft.player : null;
        if (player == null) {
            return;
        }

        List<ItemStack> playerGuns = ArmoryGunQueries.getPlayerGuns(player);
        for (ItemStack gun : playerGuns) {
            if (displayGuns.size() >= DISPLAY_GUN_COUNT) {
                break;
            }
            displayGuns.add(gun);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        Player player = minecraft != null ? minecraft.player : null;
        ArmoryInstance armory = player != null ? ArmoryManager.getBoundOrNull(player) : null;
        if (armory == null) {
            guiGraphics.drawString(font, Component.translatable("screen.universalarmory.armory_work.unbound"), 20, 48, 0xFF8080, false);
            return;
        }

        ArmoryBlockEntity blockEntity = armory.getBlockEntity();
        int slotCount = blockEntity != null ? blockEntity.getContainerSize() : 0;

        guiGraphics.drawString(font, Component.translatable("screen.universalarmory.armory_work.bound"), 20, 48, 0x80FF80, false);
        guiGraphics.drawString(font, Component.literal("Slots: " + slotCount), 20, 64, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.literal("Rifles: " + displayGuns.size()), 20, 78, 0xFFFFFF, false);

        renderGunGrid(guiGraphics);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Player player = minecraft != null ? minecraft.player : null;
        if (player != null) {
            ArmoryManager.unbind(player);
        }
        super.onClose();
    }

    private void renderGunGrid(@NotNull GuiGraphics guiGraphics) {
        int maxColumns = 6;
        int horizontalGap = Math.max(6, width / 96);
        int verticalGap = Math.max(14, height / 54);
        int previewWidth = Math.max(72, Math.min(120, (width - 24 - horizontalGap * (maxColumns - 1)) / maxColumns));
        int previewHeight = Math.max(32, previewWidth * 42 / 120);
        int gridTop = Math.max(72, height / 7);
        int rowIndex = 0;
        int gunIndex = 0;

        for (int columns : ROW_LAYOUT) {
            int rowWidth = columns * previewWidth + (columns - 1) * horizontalGap;
            int startX = Math.max(8, (width - rowWidth) / 2);
            int y = gridTop + rowIndex * (previewHeight + verticalGap);

            for (int column = 0; column < columns && gunIndex < displayGuns.size(); column++) {
                ItemStack gun = displayGuns.get(gunIndex);
                gunIndex++;
                int x = startX + column * (previewWidth + horizontalGap);

                renderGunPreview(guiGraphics, gun, x, y, previewWidth, previewHeight);
                guiGraphics.drawCenteredString(font, trimName(gun.getHoverName().getString(), previewWidth), x + previewWidth / 2, y + previewHeight + 6, 0xFFFFFF);
            }
            rowIndex++;
        }
    }

    private void renderGunPreview(@NotNull GuiGraphics guiGraphics,
                                  @NotNull ItemStack gun,
                                  int x,
                                  int y,
                                  int width,
                                  int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0x22000000);
        if (minecraft == null) {
            return;
        }
        previewRenderer.renderPreview(
                guiGraphics.pose(),
                minecraft.renderBuffers().bufferSource(),
                gun,
                x,
                y,
                width,
                height
        );
    }

    @NotNull
    private String trimName(@NotNull String name, int previewWidth) {
        if (font.width(name) <= previewWidth) {
            return name;
        }
        return font.plainSubstrByWidth(name, Math.max(20, previewWidth - 12)) + "...";
    }
}

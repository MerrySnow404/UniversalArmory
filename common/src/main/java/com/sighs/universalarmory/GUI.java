package com.sighs.universalarmory;

import com.sighs.universalarmory.gui.ArmoryScreen;
import net.minecraft.client.Minecraft;

public final class GUI {
    private GUI() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new ArmoryScreen());
    }

    public static void close() {
        Minecraft.getInstance().setScreen(null);
    }

    public static void openBound(net.minecraft.core.BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !(minecraft.level.getBlockEntity(pos) instanceof com.sighs.universalarmory.block.entity.ArmoryBlockEntity armory)) {
            return;
        }
        com.sighs.universalarmory.armory.ArmoryManager.bind(minecraft.player, armory);
        com.sighs.universalarmory.armory.ArmoryClientState.begin(pos);
        if (minecraft.screen instanceof ArmoryScreen screen) {
            screen.resetBoundView();
        } else {
            open();
        }
    }
}

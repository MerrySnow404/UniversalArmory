package com.sighs.universalarmory;

import com.sighs.apricityui.ApricityUI;
import com.sighs.universalarmory.gui.ArmoryWorkScreen;
import com.sighs.universalarmory.gui.Index;
import com.sighs.universalarmory.gui.util.AttachmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 万用军械库 GUI 入口。
 *
 * <p>负责监听按键事件打开改枪 UI，以及管理 ApricityUI 屏幕生命周期。
 */
@Mod.EventBusSubscriber(modid = Universalarmory.MODID, value = Dist.CLIENT)
public class GUI {

    private static final String UI_PATH = "uams/example1.html";

    /**
     * 打开改枪 UI。
     * <p>
     * 外部按键绑定或命令调用此方法。
     */
    public static void open() {
        // 在打开前扫描玩家背包中的枪械
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            Index.getInstance().scanInventory(player);
        }
        ApricityUI.openScreen(UI_PATH);
    }

    /**
     * 关闭当前 UI。
     */
    public static void close() {
        ApricityUI.closeScreen();
    }

    public static void openArmoryWorkScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ArmoryWorkScreen());
    }
}

package com.sighs.universalarmory;

import com.sighs.apricityui.instance.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Universalarmory.MODID, value = Dist.CLIENT)
public class Test {
    private static final String DOC_PATH = "uams/example1.html";
    private static final String AUTO_EXIT_SECONDS_PROPERTY = "apricityui.test.autoExitSeconds";
    private static final String DIAG_PROPERTY = "apricityui.test.logStyles";
    private static final String TEST_LOG_PROPERTY = "apricityui.test.verboseLogs";
    private static final long OPEN_DELAY_TICKS = 3L;
    private static final long NO_AUTO_EXIT = -1L;
    private static final long DEFAULT_AUTO_EXIT_SECONDS = 5L;

    private static long clientTickCount = 0L;
    private static long screenOpenTick = -1L;
    private static long titleSeenTick = -1L;
    private static boolean closeRequested = false;
    private static boolean loggedStyles = false;
    private static boolean screenOpened = false;
    private static boolean loggedTitleSeen = false;
    private static boolean loggedOpenAttempt = false;
    private static boolean loggedOverlayState = false;
    private static long overlayClearedTick = -1L;
    private static long lastScreenClassLogTick = -1L;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        ensureTestScreen();
    }

    private static void ensureTestScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        if (minecraft.screen instanceof TitleScreen) {
            minecraft.setScreen(new ApricityScreen(DOC_PATH));
            return;
        }
    }
}

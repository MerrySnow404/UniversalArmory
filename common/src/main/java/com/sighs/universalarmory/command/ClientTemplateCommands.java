package com.sighs.universalarmory.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.sighs.universalarmory.Universalarmory;
import com.sighs.universalarmory.template.GunTemplateCodecs;
import com.sighs.universalarmory.template.GunTemplateShareService;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Universalarmory.MODID, value = Dist.CLIENT)
public final class ClientTemplateCommands {
    private static final GunTemplateShareService SHARE_SERVICE = GunTemplateCodecs.share();

    private ClientTemplateCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("uat_share_held")
                        .executes(context -> shareHeld(context.getSource(), null))
                        .then(Commands.argument("templateName", StringArgumentType.greedyString())
                                .executes(context -> shareHeld(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "templateName")
                                )))
        );
    }

    private static int shareHeld(CommandSourceStack source, String templateNameArg) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            source.sendFailure(Component.literal("No local player is available."));
            return 0;
        }

        ItemStack heldStack = minecraft.player.getMainHandItem();
        if (!SHARE_SERVICE.supports(heldStack)) {
            source.sendFailure(Component.literal("Main hand item is not a gun."));
            return 0;
        }

        String templateName = normalizeTemplateName(templateNameArg, heldStack);
        String code = SHARE_SERVICE.exportCode(heldStack, templateName);

        if (minecraft.keyboardHandler != null) {
            minecraft.keyboardHandler.setClipboard(code);
        }

        source.sendSuccess(() -> Component.literal("Template name: " + templateName), false);
        source.sendSuccess(() -> Component.literal("Gun template code copied to clipboard:"), false);
        source.sendSuccess(() -> Component.literal(code), false);
        return 1;
    }

    private static String normalizeTemplateName(String templateNameArg, ItemStack heldStack) {
        if (templateNameArg != null) {
            String trimmed = templateNameArg.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }

        ResourceLocation gunItemId = ForgeRegistries.ITEMS.getKey(heldStack.getItem());
        return gunItemId != null ? "template_" + gunItemId.getPath() : "template_shared_gun";
    }
}

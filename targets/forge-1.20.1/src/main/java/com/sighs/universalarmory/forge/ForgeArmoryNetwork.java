package com.sighs.universalarmory.forge;

import com.sighs.universalarmory.Universalarmory;
import com.sighs.universalarmory.armory.ArmoryClientState;
import com.sighs.universalarmory.armory.ArmoryRequestHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeArmoryNetwork {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Universalarmory.MODID, "armory"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private ForgeArmoryNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Request::encode).decoder(Request::decode)
                .consumerMainThread((request, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) {
                        CompoundTag data = ArmoryRequestHandler.handle(player, request.pos, request.action, request.slot,
                                request.entryId, request.amount, request.page, request.options);
                        if (data != null) {
                            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Response(request.pos, request.requestId, data));
                        }
                    }
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(Response.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Response::encode).decoder(Response::decode)
                .consumerMainThread((response, context) -> {
                    ArmoryClientState.accept(response.pos, response.requestId, response.data);
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(Open.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> buffer.writeBlockPos(packet.pos))
                .decoder(buffer -> new Open(buffer.readBlockPos()))
                .consumerMainThread((packet, context) -> {
                    com.sighs.universalarmory.GUI.openBound(packet.pos);
                    context.get().setPacketHandled(true);
                }).add();
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Open(pos));
    }

    public static void request(String action, int slot, String entryId, int amount, int page) {
        send(action, slot, entryId, amount, page, ArmoryClientState.options());
    }

    public static void operate(String action, String entryId, CompoundTag operation, int page) {
        CompoundTag options = ArmoryClientState.options();
        options.put("Operation", operation.copy());
        send(action, -1, entryId, 0, page, options);
    }

    private static void send(String action, int slot, String entryId, int amount, int page, CompoundTag options) {
        BlockPos pos = ArmoryClientState.position();
        if (pos != null) {
            CHANNEL.sendToServer(new Request(pos, ArmoryClientState.nextRequest(), action, slot, entryId, amount, page, options));
        }
    }

    private record Open(BlockPos pos) { }

    private record Request(BlockPos pos, long requestId, String action, int slot, String entryId, int amount,
                           int page, CompoundTag options) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarLong(requestId);
            buffer.writeUtf(action, 32);
            buffer.writeVarInt(slot);
            buffer.writeUtf(entryId, 64);
            buffer.writeVarInt(amount);
            buffer.writeVarInt(page);
            buffer.writeNbt(options);
        }

        private static Request decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            long requestId = buffer.readVarLong();
            String action = buffer.readUtf(32);
            int slot = buffer.readVarInt();
            String entryId = buffer.readUtf(64);
            int amount = buffer.readVarInt();
            int page = buffer.readVarInt();
            CompoundTag options = buffer.readNbt();
            return new Request(pos, requestId, action, slot, entryId, amount, page,
                    options == null ? new CompoundTag() : options);
        }
    }

    private record Response(BlockPos pos, long requestId, CompoundTag data) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarLong(requestId);
            buffer.writeNbt(data);
        }

        private static Response decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            long requestId = buffer.readVarLong();
            CompoundTag data = buffer.readNbt();
            return new Response(pos, requestId, data == null ? new CompoundTag() : data);
        }
    }
}

package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmoryManager {

    private static final Map<UUID, ArmoryInstance> CLIENT_BOUND_INSTANCES = new ConcurrentHashMap<>();
    private static final Map<UUID, ArmoryInstance> SERVER_BOUND_INSTANCES = new ConcurrentHashMap<>();

    private ArmoryManager() {
    }

    @NotNull
    public static ArmoryInstance bind(@NotNull Player player, @NotNull ArmoryBlockEntity blockEntity) {
        ArmoryInstance instance = new ArmoryInstance(blockEntity);
        getBoundMap(player).put(player.getUUID(), instance);
        return instance;
    }

    @NotNull
    public static Optional<ArmoryInstance> getBound(@NotNull Player player) {
        Map<UUID, ArmoryInstance> boundMap = getBoundMap(player);
        ArmoryInstance instance = boundMap.get(player.getUUID());
        if (instance == null) {
            return Optional.empty();
        }
        if (!instance.isValid()) {
            boundMap.remove(player.getUUID());
            return Optional.empty();
        }
        return Optional.of(instance);
    }

    @Nullable
    public static ArmoryInstance getBoundOrNull(@NotNull Player player) {
        return getBound(player).orElse(null);
    }

    public static void unbind(@NotNull Player player) {
        getBoundMap(player).remove(player.getUUID());
    }

    @NotNull
    private static Map<UUID, ArmoryInstance> getBoundMap(@NotNull Player player) {
        return player.level().isClientSide ? CLIENT_BOUND_INSTANCES : SERVER_BOUND_INSTANCES;
    }
}

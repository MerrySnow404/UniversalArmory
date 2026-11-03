package com.sighs.universalarmory.spi;

import java.util.Objects;

public final class ArmoryServices {
    private static ArmoryPlatform platform;

    private ArmoryServices() {
    }

    public static void install(ArmoryPlatform implementation) {
        if (platform != null) {
            throw new IllegalStateException("Armory platform already installed");
        }
        platform = Objects.requireNonNull(implementation);
    }

    public static ArmoryPlatform platform() {
        if (platform == null) {
            throw new IllegalStateException("Armory platform has not been installed by the target entry point");
        }
        return platform;
    }
}

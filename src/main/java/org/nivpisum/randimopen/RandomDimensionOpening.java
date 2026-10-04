package org.nivpisum.randimopen;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(RandomDimensionOpening.MOD_ID)
public final class RandomDimensionOpening {
    public static final String MOD_ID = "randimopen";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ForgeConfigSpec.DoubleValue COORDINATE_SCALE;
    private static final ForgeConfigSpec CONFIG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COORDINATE_SCALE = builder
            .comment("Horizontal candidate weight: (scale^2 + x^2 + z^2)^-2.",
                     "Applies only when a world first selects its spawn. Existing choices are retained.",
                     "1024 blocks is approximately the unbounded distribution's median radius.")
            .defineInRange("coordinate_scale", 1024.0, 1.0, 1_000_000.0);
        CONFIG = builder.build();
    }

    public RandomDimensionOpening() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG);
    }
}

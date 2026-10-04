package org.nivpisum.randimopen.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Invoker("setInitialSpawn")
    static void randimopen$setInitialSpawn(ServerLevel level, ServerLevelData data, boolean bonusChest, boolean debug) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}

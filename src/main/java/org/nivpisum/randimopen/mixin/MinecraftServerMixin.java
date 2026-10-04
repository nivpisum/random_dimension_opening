package org.nivpisum.randimopen.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.ServerLevelData;
import org.nivpisum.randimopen.SpawnService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void randimopen$deferUntilDimensionsExist(ServerLevel level, ServerLevelData data,
                                                           boolean bonusChest, boolean debug, CallbackInfo ci) {
        if (SpawnService.deferInitialSpawn(level, bonusChest, debug)) ci.cancel();
    }

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void randimopen$selectWorldSpawn(ChunkProgressListener progress, CallbackInfo ci) {
        SpawnService.initialize((MinecraftServer) (Object) this);
    }

    @Redirect(method = "setInitialSpawn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/biome/Climate$Sampler;findSpawnPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos randimopen$weightedSearchOrigin(Climate.Sampler sampler) {
        return SpawnService.searchOrigin(sampler);
    }

    @Redirect(method = "prepareLevels", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel randimopen$prepareSelectedSpawnChunks(MinecraftServer server) {
        return SpawnService.selectedLevel(server);
    }
}

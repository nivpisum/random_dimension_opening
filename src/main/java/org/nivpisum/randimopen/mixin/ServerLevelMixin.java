package org.nivpisum.randimopen.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.nivpisum.randimopen.SpawnService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "setDefaultSpawnPos", at = @At("TAIL"))
    private void randimopen$saveSelectedDimensionCoordinates(BlockPos point, float angle, CallbackInfo ci) {
        SpawnService.retainWorldSpawnCoordinates((ServerLevel) (Object) this, point, angle);
    }
}

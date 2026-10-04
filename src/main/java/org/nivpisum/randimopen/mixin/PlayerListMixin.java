package org.nivpisum.randimopen.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.nivpisum.randimopen.SpawnService;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow @Final private MinecraftServer server;

    @Redirect(method = {"getPlayerForLogin", "respawn"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel randimopen$useWorldSpawnDimension(MinecraftServer server) {
        // In respawn this is reached only AFTER vanilla's single bed/anchor validation returned empty.
        return SpawnService.selectedLevel(server);
    }

    @Redirect(method = "placeNewPlayer", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, ordinal = 1,
        target = "Lnet/minecraft/world/level/Level;OVERWORLD:Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> randimopen$dimensionForUnsavedPlayer() {
        // Ordinal 0 is the existing-player NBT decode fallback, which intentionally remains vanilla.
        return SpawnService.selectedLevel(server).dimension();
    }
}

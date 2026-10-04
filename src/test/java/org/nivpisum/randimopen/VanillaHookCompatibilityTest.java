package org.nivpisum.randimopen;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Fail if the pinned Forge baseline no longer has the exact vanilla branches our hooks depend on. */
class VanillaHookCompatibilityTest {
    @Test void newPlayerDimensionFieldOrdinalIsStillTheUnsavedPlayerBranch() throws IOException {
        List<String> instructions = calls("net/minecraft/server/players/PlayerList", "placeNewPlayer");
        assertEquals(2, instructions.stream().filter(s -> s.equals("FIELD net/minecraft/world/level/Level OVERWORLD")).count());
        assertTrue(instructions.indexOf("CALL net/minecraft/server/players/PlayerList load")
            < instructions.indexOf("FIELD net/minecraft/world/level/Level OVERWORLD"));
    }

    @Test void vanillaValidatesThePersonalPointOnceBeforeOurFallback() throws IOException {
        List<String> instructions = calls("net/minecraft/server/players/PlayerList", "respawn");
        String validate = "CALL net/minecraft/world/entity/player/Player findRespawnPositionAndUseSpawnBlock";
        String fallback = "CALL net/minecraft/server/MinecraftServer overworld";
        assertEquals(1, instructions.stream().filter(validate::equals).count());
        assertEquals(1, instructions.stream().filter(fallback::equals).count());
        assertTrue(instructions.indexOf(validate) < instructions.indexOf(fallback));
        assertTrue(instructions.contains("CALL java/util/Optional isPresent"));
    }

    @Test void nativeSpawnSearchAndRegionPreparationAreReusable() throws IOException {
        List<String> spawn = calls("net/minecraft/server/MinecraftServer", "setInitialSpawn");
        assertEquals(1, spawn.stream().filter(s -> s.equals("CALL net/minecraft/world/level/biome/Climate$Sampler findSpawnPosition")).count());
        assertTrue(spawn.contains("CALL net/minecraft/server/level/PlayerRespawnLogic getSpawnPosInChunk"));
        assertTrue(spawn.contains("CALL net/minecraftforge/event/ForgeEventFactory onCreateWorldSpawn"));
        assertEquals(1, calls("net/minecraft/server/MinecraftServer", "prepareLevels").stream()
            .filter(s -> s.equals("CALL net/minecraft/server/MinecraftServer overworld")).count());
    }

    private static List<String> calls(String type, String method) throws IOException {
        List<String> instructions = new ArrayList<>();
        try (InputStream in = VanillaHookCompatibilityTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(in, "Missing mapped Minecraft baseline " + type);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals(method)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            instructions.add("CALL " + owner + " " + name);
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.GETSTATIC) instructions.add("FIELD " + owner + " " + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return instructions;
    }
}

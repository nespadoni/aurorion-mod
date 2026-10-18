package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/** Ponte opcional para a transferencia atomica do aurorion-vidas, sem executar comandos. */
public final class LivesMagic {
    private static boolean initialized;
    private static Method canTransferLife, transferLife;

    private LivesMagic() { }

    public static synchronized boolean available() {
        if (!initialized) {
            initialized = true;
            if (!ModList.get().isLoaded("aurorion_vidas")) return false;
            try {
                Class<?> manager = Class.forName("com.aurorion.vidas.lives.LivesManager");
                canTransferLife = manager.getMethod("canTransferLife", MinecraftServer.class, UUID.class, UUID.class);
                transferLife = manager.getMethod("transferLife", MinecraftServer.class, UUID.class, UUID.class);
            } catch (ReflectiveOperationException | LinkageError error) {
                transferLife = null;
                AurorionMagia.LOGGER.error("Magias de vida: API de aurorion-vidas indisponivel.", error);
            }
        }
        return transferLife != null;
    }

    public static boolean canTransfer(ServerPlayer donor, ServerPlayer recipient) {
        if (!available() || donor.server != recipient.server) return false;
        try {
            return (boolean) canTransferLife.invoke(null, donor.server, donor.getUUID(), recipient.getUUID());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Falha ao consultar vidas para magia", error);
        }
    }

    public static boolean transfer(ServerPlayer donor, ServerPlayer recipient) {
        if (!available() || donor.server != recipient.server) return false;
        try {
            return (boolean) transferLife.invoke(null, donor.server, donor.getUUID(), recipient.getUUID());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Falha ao ajustar vidas por magia", error);
        }
    }
}

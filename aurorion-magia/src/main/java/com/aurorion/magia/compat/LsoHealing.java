package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Ponte opcional para a API de ferimentos do LSO 2.4.7.2. */
public final class LsoHealing {
    private static boolean initialized;
    private static Method attachment, damageOf, setDamage, setDirty, updateBrokenHearts, treatCritical;
    private static Field enabled, bodyParts;
    private static Object[] parts;

    private LsoHealing() { }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("legendarysurvivaloverhaul")) return;
        try {
            String base = "sfiomn.legendarysurvivaloverhaul.";
            Class<?> body = Class.forName(base + "common.attachments.bodydamage.BodyDamageAttachment");
            Class<?> part = Class.forName(base + "api.bodydamage.BodyPartEnum");
            attachment = Class.forName(base + "util.AttachmentUtil")
                    .getMethod("getBodyDamageAttachment", Player.class);
            damageOf = body.getMethod("getBodyPartDamage", part);
            setDamage = body.getMethod("setBodyPartDamage", part, float.class);
            setDirty = body.getMethod("setManualDirty");
            updateBrokenHearts = body.getMethod("updateBrokenHearts", Player.class);
            bodyParts = body.getDeclaredField("bodyParts");
            bodyParts.setAccessible(true);
            enabled = Class.forName(base + "config.Config$Baked").getField("localizedBodyDamageEnabled");
            parts = part.getEnumConstants();
            if (ModList.get().isLoaded("aurorion_profissoes")) {
                treatCritical = Class.forName("com.aurorion.profissoes.compat.WoundPart")
                        .getMethod("aurorionTreat");
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            parts = null;
            AurorionMagia.LOGGER.warn("Nao foi possivel integrar a cura com o LSO.", error);
        }
    }

    public static void healMostWounded(ServerPlayer player, float amount) {
        if (!initialized) init();
        if (parts == null) return;
        try {
            if (!enabled.getBoolean(null)) return;
            Object body = attachment.invoke(null, player);
            Object wounded = null;
            float worst = 0;
            for (Object part : parts) {
                float damage = (float) damageOf.invoke(body, part);
                if (damage > worst) {
                    worst = damage;
                    wounded = part;
                }
            }
            if (wounded == null) return;
            float remaining = Math.max(0, worst - amount);
            setDamage.invoke(body, wounded, remaining);
            if (remaining == 0 && treatCritical != null) {
                Object part = ((Map<?, ?>) bodyParts.get(body)).get(wounded);
                if (part != null) treatCritical.invoke(part);
            }
            updateBrokenHearts.invoke(body, player);
            setDirty.invoke(body);
        } catch (ReflectiveOperationException error) {
            parts = null;
            AurorionMagia.LOGGER.warn("Falha ao curar ferimento com o LSO.", error);
        }
    }
}

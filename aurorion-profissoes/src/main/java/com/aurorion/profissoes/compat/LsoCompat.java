package com.aurorion.profissoes.compat;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import java.lang.reflect.*;
import java.util.*;

/** Reflexao resolvida uma vez; usada apenas na abertura/aceite da consulta e na serializacao. */
public final class LsoCompat {
    private static Method attachment, partEnum, setManualDirty, updateBrokenHearts;
    private static Field parts, enabled;
    private static boolean initialized, available;
    private LsoCompat() {}
    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("legendarysurvivaloverhaul")) return;
        try {
            String base = "sfiomn.legendarysurvivaloverhaul.";
            attachment = Class.forName(base + "util.AttachmentUtil").getMethod("getBodyDamageAttachment", Player.class);
            parts = Class.forName(base + "common.attachments.bodydamage.BodyDamageAttachment").getDeclaredField("bodyParts");
            parts.setAccessible(true);
            partEnum = Class.forName(base + "common.attachments.bodydamage.BodyPart").getMethod("getBodyPartEnum");
            Class<?> attachmentType = Class.forName(base + "common.attachments.bodydamage.BodyDamageAttachment");
            setManualDirty = attachmentType.getMethod("setManualDirty");
            updateBrokenHearts = attachmentType.getMethod("updateBrokenHearts", Player.class);
            enabled = Class.forName(base + "config.Config$Baked").getField("localizedBodyDamageEnabled");
            available = true;
        } catch (ReflectiveOperationException | LinkageError error) {
            AurorionProfissoes.LOGGER.error("Profissoes: API de ferimentos LSO incompativel; atendimento indisponivel.", error);
        }
    }
    public static boolean available() {
        init();
        try { return available && enabled.getBoolean(null); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
    @SuppressWarnings("unchecked")
    public static Map<? extends Enum<?>, WoundPart> parts(Player player) {
        if (!available()) return Map.of();
        try { return (Map<? extends Enum<?>, WoundPart>)parts.get(attachment.invoke(null, player)); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Falha ao ler ferimentos LSO", error); }
    }
    public static WoundPart part(Player player, String name) {
        for (var entry : parts(player).entrySet()) if (entry.getKey().name().equals(name)) return entry.getValue();
        return null;
    }
    public static boolean hasWounds(Player player) {
        for (WoundPart part : parts(player).values())
            if (part.aurorionCritical() || part.aurorionHealth() < part.aurorionMaxHealth()) return true;
        return false;
    }
    public static void healAll(Player player) {
        if (!hasWounds(player)) return;
        for (WoundPart part : parts(player).values()) part.aurorionTreat();
        markDirty(player);
    }
    public static void treat(Player player, WoundPart part) {
        part.aurorionTreat();
        markDirty(player);
    }
    private static void markDirty(Player player) {
        try {
            Object body = attachment.invoke(null, player);
            updateBrokenHearts.invoke(body, player);
            setManualDirty.invoke(body);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Falha ao sincronizar ferimentos LSO", error);
        }
    }
    public static String name(Object part) {
        init();
        try { return ((Enum<?>)partEnum.invoke(part)).name(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static String label(String name) {
        return switch (name) {
            case "HEAD" -> "Cabeça"; case "CHEST" -> "Tronco";
            case "LEFT_ARM" -> "Braço esquerdo"; case "RIGHT_ARM" -> "Braço direito";
            case "LEFT_LEG" -> "Perna esquerda"; case "RIGHT_LEG" -> "Perna direita";
            case "LEFT_FOOT" -> "Pé esquerdo"; case "RIGHT_FOOT" -> "Pé direito";
            default -> name;
        };
    }
}

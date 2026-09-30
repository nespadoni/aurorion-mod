package com.aurorion.essentials.compat;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tira do telefone, no servidor, o que era do personagem que morreu: perfil e posts do Gram e do
 * Twitter, comentarios, curtidas, DMs, stories, seguidores e os anuncios do marketplace.
 *
 * <p>O telefone guarda tudo isso pelo <b>nick</b> ({@code server_social.properties} na raiz do servidor
 * e {@code marketplace_server.properties} no mundo). A morte com personagem novo mantem o nick, entao o
 * personagem novo abria o Gram com o perfil, as fotos e as conversas do morto. O
 * {@link MattupolisPhoneCompat} ja limpava numero e banco, que sao por UUID; isto e a parte por nick.
 *
 * <p>Os stores do telefone ficam em memoria e regravam o arquivo inteiro a cada mudanca, entao mexer
 * so no arquivo nao adiantaria: a limpeza e feita nas colecoes estaticas, por reflexao, dentro do
 * mesmo monitor que os metodos {@code synchronized} do telefone usam, e o proprio store grava depois.
 * Uma entrada sai quando qualquer campo de texto dela e o nick (autor, seguidor, destinatario...), ou
 * quando a chave do mapa e o nick.
 *
 * <p>Fica de fora o que e moderacao, e nao do personagem: bloqueio de app pela staff, permissao de
 * admin social e a lista de OPs. Punicao e cargo sao da conta.
 *
 * <p>Melhor esforco: o reset do personagem nao pode parar porque o telefone mudou. Falhou, avisa no
 * log e segue.
 */
public final class PhoneSocialReset {
    private static final String SOCIAL = "com.mattupolis.phone.server.social.PhoneSocialServerStore";
    private static final String MARKETPLACE = "com.mattupolis.phone.server.marketplace.PhoneMarketplaceServerStore";
    private static final Set<String> KEPT = Set.of(
            "TWITTER_APP_BLOCKS", "GRAM_APP_BLOCKS", "SOCIAL_ADMIN_PERMISSIONS", "SERVER_OP_KEYS");

    private PhoneSocialReset() {
    }

    public static void forget(MinecraftServer server, String nick) {
        if (nick == null || nick.isBlank()) return;
        String key = normalize(nick);
        forgetSocial(key);
        forgetMarketplace(server, key);
    }

    private static void forgetSocial(String key) {
        try {
            Class<?> store = Class.forName(SOCIAL);
            synchronized (store) {
                invokeStatic(store, "ensurePersistentLoaded");
                if (removeFromStaticCollections(store, key)) invokeStatic(store, "savePersistentStore");
            }
        } catch (ClassNotFoundException ignored) {
            // O telefone e opcional.
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui apagar o Gram/Twitter do personagem anterior ({}).", key, e);
        }
    }

    private static void forgetMarketplace(MinecraftServer server, String key) {
        try {
            Class<?> store = Class.forName(MARKETPLACE);
            synchronized (store) {
                Method ensureLoaded = store.getDeclaredMethod("ensureLoaded", MinecraftServer.class);
                ensureLoaded.setAccessible(true);
                ensureLoaded.invoke(null, server);
                if (removeFromStaticCollections(store, key)) {
                    Method save = store.getDeclaredMethod("save", MinecraftServer.class);
                    save.setAccessible(true);
                    save.invoke(null, server);
                }
            }
        } catch (ClassNotFoundException ignored) {
            // O telefone e opcional.
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui apagar os anuncios do personagem anterior ({}).", key, e);
        }
    }

    /** @return se alguma colecao mudou */
    private static boolean removeFromStaticCollections(Class<?> store, String key) throws IllegalAccessException {
        boolean changed = false;
        for (Field field : store.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || KEPT.contains(field.getName())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                changed |= map.entrySet().removeIf(entry -> mentions(entry.getKey(), key) || mentions(entry.getValue(), key));
            } else if (value instanceof Collection<?> collection) {
                changed |= collection.removeIf(element -> mentions(element, key));
            }
        }
        return changed;
    }

    /** O proprio texto e o nick, ou algum campo de texto do registro e. */
    static boolean mentions(Object value, String key) {
        if (value == null) return false;
        if (value instanceof String text) return normalize(text).equals(key);
        if (value.getClass().getName().startsWith("java.")) return false;
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
                try {
                    field.setAccessible(true);
                    Object text = field.get(value);
                    if (text instanceof String string && normalize(string).equals(key)) return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Campo inacessivel: nao decide nada.
                }
            }
        }
        return false;
    }

    /** A mesma chave que o telefone usa ({@code normalizePlayerKey}): sem @, minusculas, espaco vira _. */
    static String normalize(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("@")) normalized = normalized.substring(1);
        return normalized.toLowerCase(Locale.ROOT).replace(" ", "_");
    }

    private static void invokeStatic(Class<?> owner, String name) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(null);
    }
}

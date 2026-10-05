package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponte por reflexao com as telas e lojas do telefone, usada pelos anexos de foto. O telefone nao esta
 * no classpath de compilacao; cada falha aqui vira aviso no log e a acao simplesmente nao acontece,
 * sem derrubar a tela.
 */
public final class PhoneUi {
    static final String GUI = "com.mattupolis.phone.client.gui.";
    /** Tamanho do celular em todas as telas do telefone; elas o centralizam na janela. */
    public static final int PHONE_WIDTH = 170;
    public static final int PHONE_HEIGHT = 285;
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private PhoneUi() {
    }

    public static boolean is(Object screen, String simpleName) {
        return screen != null && screen.getClass().getName().equals(GUI + simpleName);
    }

    /** Cores do tema atual do telefone (claro/escuro), com um padrao se o metodo sumir. */
    public static int themeColor(String getter, int fallback) {
        Object value = callStatic("PhoneTheme", getter, new Class<?>[0]);
        return value instanceof Integer color ? color : fallback;
    }

    public static boolean airplaneMode() {
        return Boolean.TRUE.equals(callStatic("PhoneSettingsStore", "isAirplaneModeEnabled", new Class<?>[0]));
    }

    public static void playClick() {
        callStatic("PhoneSoundManager", "playClick", new Class<?>[0]);
    }

    public static void playBack() {
        callStatic("PhoneSoundManager", "playBack", new Class<?>[0]);
    }

    public static void playError() {
        callStatic("PhoneSoundManager", "playError", new Class<?>[0]);
    }

    /** Texto traduzido pelo proprio telefone (chave do idioma escolhido no celular). */
    public static String text(String key, String fallback) {
        Object value = callStatic("PhoneLanguageStore", "t", new Class<?>[]{String.class}, key);
        return value instanceof String text && !text.equals(key) ? text : fallback;
    }

    /** Aviso no balao da propria tela do celular; sem balao, vai para o chat. */
    public static void toast(String message) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            try {
                Field text = screen.getClass().getDeclaredField("toastMessage");
                Field ticks = screen.getClass().getDeclaredField("toastTicks");
                text.setAccessible(true);
                ticks.setAccessible(true);
                text.set(screen, message);
                ticks.setInt(screen, 45);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Tela sem balao de aviso: cai no chat.
            }
        }
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(message));
    }

    public static Screen newScreen(String simpleName, Class<?>[] types, Object... args) {
        try {
            return (Screen) Class.forName(GUI + simpleName).getConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException | ClassCastException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui abrir a tela {} do telefone.", simpleName, e);
            return null;
        }
    }

    public static void open(Screen screen) {
        if (screen != null) Minecraft.getInstance().setScreen(screen);
    }

    public static String stringField(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner) instanceof String value ? value : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static int phoneLeft(Screen screen) {
        return (screen.width - PHONE_WIDTH) / 2;
    }

    public static int phoneTop(Screen screen) {
        return (screen.height - PHONE_HEIGHT) / 2;
    }

    /** Chama um metodo sem argumentos da propria tela (ex.: {@code openCamera}). */
    public static void callPrivate(Object owner, String name) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(owner);
        } catch (ReflectiveOperationException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui chamar {} no telefone.", name, e);
        }
    }

    /**
     * Envia uma foto da galeria no app Mensagens pelo mesmo caminho da camera: registra a foto na
     * conversa, manda o pacote de foto do telefone (se o contato estiver online) e volta para o chat.
     */
    public static boolean sendMessagePhoto(String threadId, Path photo) {
        try {
            Class<?> session = Class.forName(GUI + "PhoneLiveCameraSession");
            Field thread = session.getDeclaredField("messageThreadId");
            thread.setAccessible(true);
            thread.set(null, threadId);
            Method send = session.getDeclaredMethod("sendMessagePhoto", Path.class);
            send.setAccessible(true);
            send.invoke(null, photo);
            return true;
        } catch (ReflectiveOperationException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui enviar a foto da galeria no Mensagens.", e);
            return false;
        }
    }

    private static Object callStatic(String simpleClass, String name, Class<?>[] types, Object... args) {
        // Metodo ausente tambem fica guardado: cores e sons sao pedidos varias vezes por quadro.
        Optional<Method> method = METHODS.computeIfAbsent(simpleClass + "#" + name, key -> {
            try {
                Method found = Class.forName(GUI + simpleClass).getDeclaredMethod(name, types);
                found.setAccessible(true);
                return Optional.of(found);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        });
        if (method.isEmpty()) return null;
        try {
            return method.get().invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}

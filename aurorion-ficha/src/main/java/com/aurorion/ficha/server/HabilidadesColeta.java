package com.aurorion.ficha.server;

import com.aurorion.core.character.CharacterAbilities;
import com.aurorion.core.house.HouseGate;
import com.aurorion.core.lives.LivesGate;
import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.sheet.Habilidades;
import com.aurorion.ficha.sheet.Json;
import com.google.gson.JsonArray;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O campo {@code habilidades} da View: o que o personagem tem no jogo, pelos provedores que os outros
 * mods registram no {@link CharacterAbilities} do core (magia, profissões, Trama…), mais o grupo
 * Personagem (Casa e vidas), que sai dos contratos que o core já tinha ({@link HouseGate},
 * {@link LivesGate}). A ficha não importa nenhum desses mods, e nenhum deles exige a ficha.
 *
 * <h2>Custo</h2>
 * Thread do servidor, só quando uma View é montada (abrir, atualizar, vincular, espiar) — nunca por
 * tick. Os provedores só leem memória. Uma falha aqui nunca impede a ficha de abrir: a aba vem vazia.
 *
 * <p>{@link #changed} deixa o {@code atualizar} da tela aberta mandar a View de novo quando as
 * habilidades mudaram (passiva ligada, magia liberada em aula), mesmo com a ficha do site igual.
 */
@EventBusSubscriber(modid = AurorionFicha.MOD_ID)
public final class HabilidadesColeta {
    /** Hash do último {@code habilidades} mandado ao dono, por perfil online. */
    private static final Map<UUID, Integer> SENT = new ConcurrentHashMap<>();

    private HabilidadesColeta() {
    }

    /** As habilidades do dono da View; lembra o que foi mandado, para o {@link #changed}. */
    static JsonArray json(ServerPlayer player) {
        JsonArray array = of(player).toJson();
        SENT.put(player.getUUID(), array.hashCode());
        return array;
    }

    /** As de outro jogador (espiar do mestre): não mexe no que o dono viu. */
    static JsonArray jsonFor(@Nullable ServerPlayer target) {
        return target == null ? new JsonArray() : of(target).toJson();
    }

    /** Mudou desde a última View que o dono recebeu? Sem View anterior, não (a próxima abertura já leva). */
    static boolean changed(ServerPlayer player) {
        Integer sent = SENT.get(player.getUUID());
        return sent != null && sent != of(player).toJson().hashCode();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    static Habilidades of(ServerPlayer player) {
        try {
            List<CharacterAbilities.Group> groups = new ArrayList<>(CharacterAbilities.collect(player));
            CharacterAbilities.Group personagem = personagem(player);
            if (personagem != null) groups.add(personagem);
            return convert(groups);
        } catch (RuntimeException e) {
            AurorionFicha.LOGGER.warn("Ficha: não deu para montar as habilidades de {}", player.getGameProfile().getName(), e);
            return Habilidades.EMPTY;
        }
    }

    /** Core → formato da View, já nos tetos do pacote. Ordem por {@code order}; grupo vazio sai. */
    static Habilidades convert(List<CharacterAbilities.Group> groups) {
        List<CharacterAbilities.Group> sorted = new ArrayList<>(groups);
        sorted.removeIf(g -> g == null || g.items().isEmpty());
        sorted.sort(Comparator.comparingInt(CharacterAbilities.Group::order));
        List<Habilidades.Grupo> out = new ArrayList<>(Math.min(sorted.size(), Habilidades.MAX_GRUPOS));
        for (CharacterAbilities.Group g : sorted) {
            if (out.size() >= Habilidades.MAX_GRUPOS) break;
            List<Habilidades.Item> itens = new ArrayList<>(Math.min(g.items().size(), Habilidades.MAX_ITENS));
            for (CharacterAbilities.Item i : g.items()) {
                if (itens.size() >= Habilidades.MAX_ITENS) break;
                itens.add(new Habilidades.Item(Json.clean(i.name(), 64), Json.clip(i.detail(), 400), Json.clean(i.level(), 24),
                        i.state().wire(), i.state() == CharacterAbilities.State.PROGRESS ? i.progress() : -1,
                        Json.clean(i.icon(), 80), Json.clean(i.nameKey(), 128), Json.clean(i.detailKey(), 128)));
            }
            out.add(new Habilidades.Grupo(Json.clean(g.id(), 32), Json.clean(g.title(), 48), g.color(), itens));
        }
        return new Habilidades(out);
    }

    /** Casa e vidas: dois contratos do core, sem importar o Ethereal nem o mod de vidas. */
    @Nullable
    static CharacterAbilities.Group personagem(ServerPlayer player) {
        List<CharacterAbilities.Item> items = new ArrayList<>(2);
        UUID id = player.getUUID();
        String color = "";
        if (HouseGate.installed()) {
            ResourceLocation house = HouseGate.of(player.server, id);
            if (house == null) {
                items.add(CharacterAbilities.Item.of("Sem Casa", "A Cerimônia de Vinculação decide a Casa do personagem.",
                        "Casa", CharacterAbilities.State.LOCKED, "minecraft:white_banner"));
            } else {
                Component name = HouseGate.nameOf(house);
                String key = name.getContents() instanceof TranslatableContents t ? t.getKey() : "";
                items.add(CharacterAbilities.Item.of(name.getString(), "A Casa do personagem.", "Casa",
                        CharacterAbilities.State.ACTIVE, "minecraft:white_banner").withKeys(key, ""));
                Integer rgb = HouseGate.colorOf(player.server, id);
                if (rgb != null) color = CharacterAbilities.color(rgb);
            }
        }
        if (LivesGate.installed()) {
            int lives = LivesGate.of(player.server, id);
            if (lives == 0) {
                items.add(CharacterAbilities.Item.of("Exílio", "Sem vidas: só a staff ou um resgate devolve uma.",
                        "0 vidas", CharacterAbilities.State.LOCKED, "minecraft:wither_rose"));
            } else if (lives > 0) {
                items.add(CharacterAbilities.Item.of("Vidas", "Cada morte gasta uma. Ao zerar, o exílio.",
                        lives == 1 ? "1 restante" : lives + " restantes", CharacterAbilities.State.ACTIVE, "minecraft:totem_of_undying"));
            }
        }
        return items.isEmpty() ? null : new CharacterAbilities.Group("personagem", "Personagem", color, 90, items);
    }
}

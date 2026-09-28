package com.aurorion.core.character;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * O segundo personagem de uma conta de staff: a conta ADM e a conta de jogador.
 *
 * <h2>Por que o alt e outro jogador, e nao outro arquivo</h2>
 *
 * <p>O alt tem UUID proprio, derivado do da conta ({@link #altIdOf}), e o servidor troca o perfil da
 * conexao por ele no login (o mixin do {@code aurorion-personagem}). Dali em diante, para o vanilla e
 * para <b>todo</b> mod, e simplesmente outra pessoa: playerdata, vidas, casa, carteira, magias, nome
 * exibido, e ate o que mods de terceiros guardam fora do arquivo do jogador ficam separados sem uma
 * linha de codigo por mod. E o {@code ops.json} tambem e por perfil — o alt nasce sem OP.
 *
 * <p>Separar os dados mod a mod (mesmo UUID, trocando arquivos) exigiria tocar em cada mod do
 * ecossistema e ainda deixaria escapar o que mod de terceiros guarda do jeito dele.
 *
 * <h2>O que mora aqui</h2>
 *
 * <p>Conta real → alt (UUID, nome tecnico de perfil, e se o alt e quem entra no proximo login), e o
 * inverso, para o alt conseguir voltar sem ser OP. Salvo com o resto dos dados do mundo; quem troca
 * grava com {@code SavedDataAccess.flushAll} antes de desconectar.
 *
 * <p>So a thread do servidor mexe aqui.
 */
public final class AltData extends SavedData {
    private static final SavedDataAccess<AltData> ACCESS =
            new SavedDataAccess<>("aurorion_core_alts", AltData::new, AltData::load);

    /** Nome de perfil tem no maximo 16 caracteres no vanilla. */
    public static final int MAX_PROFILE_NAME = 16;
    private static final String SUFFIX = "_alt";

    /**
     * @param active true quando o proximo login desta conta entra como o alt
     */
    public record Alt(UUID owner, UUID altId, String altName, boolean active) {
        Alt withActive(boolean value) {
            return new Alt(owner, altId, altName, value);
        }
    }

    private final Map<UUID, Alt> byOwner = new HashMap<>();
    private final Map<UUID, UUID> ownerOfAlt = new HashMap<>();

    public static AltData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    /** O UUID do alt: fixo para a mesma conta, entao o alt e sempre o mesmo jogador. */
    public static UUID altIdOf(UUID owner) {
        return UUID.nameUUIDFromBytes(("aurorion-alt:" + owner).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Nome tecnico de perfil do alt. E o que aparece em comando de staff e no log; o que os jogadores
     * veem e o nome do personagem (fakename). {@code attempt} &gt; 0 gera variacoes para desviar de
     * um nome ja usado.
     */
    public static String altNameOf(String ownerName, int attempt) {
        String suffix = attempt == 0 ? SUFFIX : "_a" + (attempt + 1);
        String base = ownerName.replaceAll("[^A-Za-z0-9_]", "");
        if (base.isEmpty()) base = "alt";
        int room = MAX_PROFILE_NAME - suffix.length();
        return (base.length() > room ? base.substring(0, room) : base) + suffix;
    }

    /** Nome da conta (o nick da Mojang). Cai no UUID se o perfil nao estiver no cache do servidor. */
    public static String accountName(MinecraftServer server, UUID account) {
        ServerPlayer online = server.getPlayerList().getPlayer(account);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? account.toString()
                : cache.get(account).map(GameProfile::getName).orElse(account.toString());
    }

    /**
     * Como a staff enxerga um jogador: o nome da conta e, se ele for o segundo personagem de alguem,
     * de quem ele e — {@code "NetoSpadoni_alt (alt de NetoSpadoni)"}. Para {@code /realname}, avisos
     * de area e tudo que existe para dizer quem a pessoa e.
     */
    public static String staffLabel(MinecraftServer server, GameProfile profile) {
        UUID owner = get(server).ownerOf(profile.getId());
        return owner == null ? profile.getName()
                : profile.getName() + " (alt de " + accountName(server, owner) + ")";
    }

    @Nullable
    public Alt byOwner(UUID owner) {
        return byOwner.get(owner);
    }

    /** Dono do alt {@code alt}, ou null se esse UUID nao e alt de ninguem. */
    @Nullable
    public UUID ownerOf(UUID alt) {
        return ownerOfAlt.get(alt);
    }

    public boolean isAlt(UUID id) {
        return ownerOfAlt.containsKey(id);
    }

    /** Algum alt ja usa este nome de perfil (sem diferenciar caixa)? */
    public boolean nameTaken(String profileName) {
        String key = profileName.toLowerCase(Locale.ROOT);
        for (Alt alt : byOwner.values()) {
            if (alt.altName().toLowerCase(Locale.ROOT).equals(key)) return true;
        }
        return false;
    }

    public Map<UUID, Alt> all() {
        return Collections.unmodifiableMap(byOwner);
    }

    /** Cadastra o alt da conta. Comeca inativo: quem cria ainda esta como a conta principal. */
    public Alt create(UUID owner, String altName) {
        if (byOwner.containsKey(owner)) throw new IllegalStateException("Account already has an alt");
        if (ownerOfAlt.containsKey(owner)) throw new IllegalStateException("An alt cannot own an alt");
        if (altName.isBlank() || altName.length() > MAX_PROFILE_NAME || nameTaken(altName)) {
            throw new IllegalArgumentException("Invalid alt profile name");
        }
        Alt alt = new Alt(owner, altIdOf(owner), altName, false);
        byOwner.put(owner, alt);
        ownerOfAlt.put(alt.altId(), owner);
        setDirty();
        return alt;
    }

    /** Qual dos dois entra no proximo login. */
    public Alt setActive(UUID owner, boolean active) {
        Alt alt = byOwner.get(owner);
        if (alt == null) throw new IllegalStateException("Account has no alt");
        if (alt.active() == active) return alt;
        Alt updated = alt.withActive(active);
        byOwner.put(owner, updated);
        setDirty();
        return updated;
    }

    /**
     * Desfaz o vinculo. Os arquivos do alt ficam no mundo: recriar o alt devolve o mesmo UUID e,
     * com ele, o mesmo personagem.
     */
    @Nullable
    public Alt remove(UUID owner) {
        Alt alt = byOwner.remove(owner);
        if (alt == null) return null;
        ownerOfAlt.remove(alt.altId());
        setDirty();
        return alt;
    }

    static AltData load(CompoundTag tag, HolderLookup.Provider registries) {
        AltData data = new AltData();
        PlayerMapNbt.read(tag, "Alts", data.byOwner, entry -> entry.hasUUID("AltId")
                ? new Alt(entry.getUUID(PlayerMapNbt.KEY_PLAYER), entry.getUUID("AltId"),
                        entry.getString("AltName"), entry.getBoolean("Active"))
                : null);
        data.byOwner.values().forEach(alt -> data.ownerOfAlt.put(alt.altId(), alt.owner()));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Alts", PlayerMapNbt.write(byOwner, (entry, alt) -> {
            entry.putUUID("AltId", alt.altId());
            entry.putString("AltName", alt.altName());
            entry.putBoolean("Active", alt.active());
        }));
        return tag;
    }
}

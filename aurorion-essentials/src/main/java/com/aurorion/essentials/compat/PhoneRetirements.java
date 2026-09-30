package com.aurorion.essentials.compat;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Personagens que morreram de vez (reset), para os telefones dos outros esquecerem deles.
 *
 * <p>A agenda do telefone Mattupolis fica no PC de cada jogador
 * ({@code mattupolis_phone/contacts.properties}), e cada contato e so o nick da conta — ligacao,
 * mensagem e PIX sao roteados pelo nick. Depois de um reset, o contato do morto passava a apontar
 * para o personagem novo da mesma pessoa, e ainda aparecia com o nome novo (a troca de nome do
 * essentials acha a conta pelo nick): a agenda denunciava quem era o jogador por tras do personagem.
 *
 * <p>O servidor nao alcanca o disco dos clientes, entao guarda aqui cada reset e manda a lista no
 * login ({@code PhoneContactCleanup} faz a limpeza no cliente). A chave e o id do personagem
 * aposentado, unico por reset, e o cliente processa cada um <b>uma vez so</b>: o contato que existir
 * quando ele fica sabendo do reset e do morto; um contato adicionado depois disso e do personagem novo
 * e fica.
 *
 * <p>Entradas com mais de {@link #KEEP_DAYS} dias saem da lista: quem ficar esse tempo sem entrar
 * mantem o contato antigo. O limite existe para a lista enviada no login nao crescer para sempre.
 */
public final class PhoneRetirements extends SavedData {
    private static final String FILE_ID = AurorionEssentials.MOD_ID + "_phone_retirements";
    static final long KEEP_DAYS = 180;
    private static final long KEEP_MS = TimeUnit.DAYS.toMillis(KEEP_DAYS);

    private static final SavedDataAccess<PhoneRetirements> ACCESS =
            new SavedDataAccess<>(FILE_ID, PhoneRetirements::new, PhoneRetirements::load);

    private record Retirement(String nick, long at) {
    }

    /** Id do personagem aposentado -> nick da conta. Ordem de insercao, para a lista sair estavel. */
    private final Map<UUID, Retirement> retired = new LinkedHashMap<>();

    public static PhoneRetirements get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    /** Idempotente: o reset e repetido quando cai no meio, e o id do personagem nao muda. */
    public void add(UUID previousCharacter, String nick, long now) {
        if (retired.containsKey(previousCharacter)) return;
        retired.put(previousCharacter, new Retirement(nick, now));
        setDirty();
    }

    /** O que mandar aos clientes: id do personagem aposentado -> nick. Descarta o que venceu. */
    public Map<UUID, String> active(long now) {
        if (retired.values().removeIf(entry -> now - entry.at() > KEEP_MS)) setDirty();
        Map<UUID, String> result = new LinkedHashMap<>();
        retired.forEach((id, entry) -> result.put(id, entry.nick()));
        return result;
    }

    private static PhoneRetirements load(CompoundTag tag, HolderLookup.Provider registries) {
        PhoneRetirements data = new PhoneRetirements();
        ListTag list = tag.getList("Retired", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            data.retired.put(entry.getUUID("Id"), new Retirement(entry.getString("Nick"), entry.getLong("At")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        retired.forEach((id, entry) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", id);
            row.putString("Nick", entry.nick());
            row.putLong("At", entry.at());
            list.add(row);
        });
        tag.put("Retired", list);
        return tag;
    }
}

package com.aurorion.core.death;

import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * "O espolio desta morte ja voltou para alguem?"
 *
 * <p>Quem escreve e o mod que devolveu itens do chao (o Relicario, no {@code aurorion_limbo}). Quem
 * le e quem poderia devolver <b>de novo</b> por outro caminho — o {@code /deathhistory restore}, que
 * recria o inventario a partir de uma copia. Sem este registro, a staff restauraria por cima de um
 * Relicario ja usado e os itens existiriam duas vezes.
 *
 * <p>O registro so avisa; quem decide e a staff. Por isso guarda o bastante para ela decidir — quem
 * chamou de volta, quando e quanto — e nada do conteudo.
 *
 * <h2>Tamanho</h2>
 *
 * <p>Uma entrada por morte reclamada, nunca por morte. Acima de {@link #MAX_ENTRIES} a mais antiga
 * sai: o {@link LinkedHashMap} mantem a ordem de chegada, entao descartar e O(1) e nao ha varredura
 * por idade. Na escala do servidor (dezenas de Relicarios por semana) o teto cobre anos — e uma
 * morte tao antiga ja saiu do historico do essentials antes de sair daqui.
 */
public final class DeathClaims extends SavedData {
    static final int MAX_ENTRIES = 4096;

    private static final SavedDataAccess<DeathClaims> ACCESS =
            new SavedDataAccess<>("aurorion_core_espolio", DeathClaims::new, DeathClaims::load);

    /**
     * @param source quem devolveu, como id estavel ({@code "relicario"}); vai para a tela da staff.
     * @param stacks quantas pilhas voltaram. Zero nunca e gravado: nada voltou, nada duplica.
     */
    public record Claim(String source, long claimedAt, int stacks) {
    }

    private final LinkedHashMap<UUID, Claim> claims = new LinkedHashMap<>();

    public static DeathClaims get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    static DeathClaims load(CompoundTag tag, HolderLookup.Provider registries) { // visivel para teste
        DeathClaims data = new DeathClaims();
        ListTag list = tag.getList("Claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Death")) continue;
            data.claims.put(entry.getUUID("Death"),
                    new Claim(entry.getString("Source"), entry.getLong("At"), entry.getInt("Stacks")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        claims.forEach((death, claim) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Death", death);
            entry.putString("Source", claim.source());
            entry.putLong("At", claim.claimedAt());
            entry.putInt("Stacks", claim.stacks());
            list.add(entry);
        });
        tag.put("Claims", list);
        return tag;
    }

    @Nullable
    public Claim find(UUID death) {
        return claims.get(death);
    }

    /** Soma a um registro que ja exista: dois Relicarios na mesma morte sao um so aviso, maior. */
    public void claim(UUID death, String source, int stacks) {
        if (stacks <= 0) return;

        Claim previous = claims.remove(death);
        int total = previous == null ? stacks : previous.stacks() + stacks;
        claims.put(death, new Claim(source, System.currentTimeMillis(), total));

        if (claims.size() > MAX_ENTRIES) {
            Iterator<UUID> oldest = claims.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        setDirty();
    }

    int size() { // visivel para teste
        return claims.size();
    }
}

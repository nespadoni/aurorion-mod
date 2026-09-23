package com.aurorion.core.death;

import com.aurorion.core.AurorionCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Iterator;

/**
 * Itens que ficam com o jogador quando ele morre.
 *
 * <p>Quem decide <b>quais</b> e a tag {@code aurorion_core:kept_on_death}, que qualquer mod ou
 * datapack do pack preenche — o Fio da Volta e o Relicario do {@code aurorion_limbo} entram por ela.
 * Conteudo e datapack; comportamento e codigo.
 *
 * <p>Mora no core, e nao no mod dos itens, por causa de quem precisa <b>ler</b> a regra: o
 * {@code /deathhistory restore} do essentials tem que saber que aqueles itens nao se perderam na
 * morte, ou restauraria uma segunda copia deles.
 *
 * <h2>Onde o item espera o renascer</h2>
 *
 * <p>Tirado dos drops, o item volta para o inventario do <b>corpo</b>, e nao para um mapa em
 * memoria. Se a pessoa deslogar na tela de morte, o corpo e salvo com ele, e o {@link #onClone} o
 * passa adiante quando ela finalmente renascer — nem crash nem logout perdem o item.
 *
 * <p>O custo fora da morte e zero: nenhum tick, nenhuma varredura.
 */
@EventBusSubscriber(modid = AurorionCore.MOD_ID)
public final class KeptOnDeath {
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AurorionCore.MOD_ID, "kept_on_death"));

    private KeptOnDeath() {
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TAG);
    }

    /**
     * {@code HIGHEST}: o item sai da lista antes de um mod de tumulo o levar para o tumulo, e antes
     * de qualquer um marcar os drops como espolio.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;

        Inventory inventory = player.getInventory();
        for (Iterator<ItemEntity> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemEntity drop = it.next();
            if (!is(drop.getItem())) continue;

            ItemStack rest = drop.getItem().copy();
            inventory.add(rest);
            if (rest.isEmpty()) it.remove();
            else drop.setItem(rest);
        }
    }

    /**
     * Com {@code keepInventory} o vanilla ja copia o inventario inteiro; copiar de novo aqui
     * duplicaria cada item marcado a cada morte.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        ServerPlayer reborn = event.getEntity() instanceof ServerPlayer p ? p : null;
        if (reborn == null || reborn.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        Inventory from = event.getOriginal().getInventory();
        for (int slot = 0; slot < from.getContainerSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (!is(stack)) continue;

            reborn.getInventory().add(stack.copy());
            from.setItem(slot, ItemStack.EMPTY);
        }
    }
}

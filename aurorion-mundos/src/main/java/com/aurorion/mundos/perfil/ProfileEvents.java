package com.aurorion.mundos.perfil;

import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.config.MundosConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Onde o perfil de mundo encontra o jogo. Tudo aqui e evento de algo que ja esta acontecendo — um
 * monstro nascendo, um golpe, uma morte, um clique —, nenhum por tick.
 */
@EventBusSubscriber(modid = AurorionMundos.MOD_ID)
public final class ProfileEvents {
    /** Os blocos que a trava de spawner protege. Datapack pode acrescentar os de mod. */
    public static final TagKey<Block> SPAWNERS = TagKey.create(Registries.BLOCK, AurorionMundos.id("spawners"));

    private ProfileEvents() {
    }

    // --- Monstros ------------------------------------------------------------------------------

    /** Antes de entrar no mundo: so marca quem saiu de spawner, que o perfil pode deixar de fora. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (MobSpawnType.isSpawner(event.getSpawnType())) {
            event.getEntity().getPersistentData().putBoolean(MonsterScaling.FROM_SPAWNER, true);
        }
    }

    /** {@code LOW}: depois dos mods que poem a propria vida extra (elites do Apotheosis), e por cima dela. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) {
            MonsterScaling.apply(mob, !event.loadedFromDisk());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingIncomingDamageEvent event) {
        float factor = MonsterScaling.damageFactor(event.getSource().getEntity());
        if (factor != 1) event.setAmount(event.getAmount() * factor);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExperience(LivingExperienceDropEvent event) {
        double factor = MonsterScaling.experienceFactor(event.getEntity());
        if (factor != 1) event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * factor));
    }

    // --- Trava de spawner ----------------------------------------------------------------------

    /**
     * Nada se faz <i>no</i> spawner: ovo (trocar o monstro), runa ou modificador de mod, e o Carry On
     * levando-o embora — que pega blocos neste mesmo evento, em {@code HIGH}. Spawner vanilla nao tem
     * nenhum uso de clique direito legitimo, entao a trava e total, e so o criativo da staff passa.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseSpawner(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !locked(event.getEntity())) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(SPAWNERS)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().displayClientMessage(Component.translatable("aurorion_mundos.spawner.travado")
                .withStyle(ChatFormatting.RED), true);
    }

    /** Spawner que alguem conseguiu por fora (troca, bau de estrutura, mod) nao vai para o chao. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Player player) || !locked(player)) return;
        if (!event.getPlacedBlock().is(SPAWNERS)) return;
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("aurorion_mundos.spawner.travado")
                .withStyle(ChatFormatting.RED), true);
    }

    private static boolean locked(Player player) {
        return MundosConfig.SPEC.isLoaded() && MundosConfig.LOCK_SPAWNERS.get()
                && !(player.isCreative() && player.hasPermissions(2));
    }
}

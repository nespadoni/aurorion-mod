package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.core.compat.TwilightTeleportCompat;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.UUID;

/** Duelo entre jogadores em uma dimensao real. Nao utiliza o Limbo nem seus dados de vidas. */
public final class SoulRealm {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            AurorionMagia.id("reino_almas"));
    private SoulRealm() { }

    public static boolean active(LivingEntity entity) {
        return entity instanceof ServerPlayer player && SoulRealmData.get(player.server).anchor(player.getUUID()) != null;
    }

    public static boolean start(ServerPlayer caster, ServerPlayer target, int ticks) {
        ServerLevel realm = caster.server.getLevel(DIMENSION);
        if (realm == null) {
            caster.sendSystemMessage(Component.translatable("aurorion_magia.reino_indisponivel"));
            return false;
        }
        if (active(caster) || active(target) || caster.level().dimension().equals(DIMENSION)
                || target.level().dimension().equals(DIMENSION)) return false;
        SoulRealmData data = SoulRealmData.get(caster.server);
        Vec3 center = data.allocate();
        if (center == null) { caster.sendSystemMessage(Component.translatable("aurorion_magia.arena_ocupada")); return false; }
        try {
            prepare(realm, center);
            UUID duel = UUID.randomUUID();
            data.remember(caster, target, duel, center, true);
            data.remember(target, caster, duel, center, false);
            Domains.stop(caster);
            Domains.stop(target);
            caster.stopRiding(); target.stopRiding();
            if (MagicData.getPlayerMagicData(target).isCasting()) Utils.serverSideCancelCast(target);
            target.stopUsingItem();
            TwilightTeleportCompat.immediateRun(() -> caster.teleportTo(realm, center.x - 5, center.y, center.z, Set.of(), -90, 0));
            TwilightTeleportCompat.immediateRun(() -> target.teleportTo(realm, center.x + 5, center.y, center.z, Set.of(), 90, 0));
            // Um outro mod pode recusar uma transicao. Nunca deixa metade do duelo presa.
            if (caster.level() != realm || target.level() != realm) { end(caster); return false; }
            caster.addEffect(new MobEffectInstance(MagiaEffects.SOUL_REALM, ticks, 1, false, false, true));
            target.addEffect(new MobEffectInstance(MagiaEffects.SOUL_REALM, ticks, 0, false, false, true));
            com.aurorion.magia.entity.SpellZoneEntity.create(realm, caster,
                    com.aurorion.magia.entity.SpellZoneEntity.Shape.SOUL_ARENA, center, 16, 24, ticks, 1, Vec3.ZERO);
            caster.resetFallDistance(); target.resetFallDistance();
            AurorionSpell.sound(caster, SoundEvents.SCULK_SHRIEKER_SHRIEK, 2, .55F);
            AurorionSpell.sound(target, SoundEvents.SOUL_ESCAPE.value(), 2, .6F);
            caster.displayClientMessage(Component.translatable("aurorion_magia.reino_duelo"), true);
            target.displayClientMessage(Component.translatable("aurorion_magia.reino_duelo"), true);
            return true;
        } catch (RuntimeException failure) {
            end(caster);
            caster.sendSystemMessage(Component.translatable("aurorion_magia.reino_indisponivel"));
            AurorionMagia.LOGGER.error("Falha ao entrar no Reino das Almas", failure);
            return false;
        }
    }

    private static void prepare(ServerLevel level, Vec3 center) {
        BlockPos origin = BlockPos.containing(center);
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
            boolean edge = Math.abs(x) == 16 || Math.abs(z) == 16;
            for (int y = 0; y < 8; y++) {
                level.setBlock(origin.offset(x, y, z), edge ? Blocks.BEDROCK.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 3);
            }
            if (!edge && (x * x + z * z) % 37 == 0) {
                level.setBlock(origin.offset(x, 0, z), Blocks.SOUL_TORCH.defaultBlockState(), 3);
            }
        }
    }

    public static boolean tick(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        SoulRealmData data = SoulRealmData.get(player.server);
        CompoundTag a = data.anchor(player.getUUID());
        if (a == null) return false;
        ServerPlayer peer = player.server.getPlayerList().getPlayer(a.getUUID("Peer"));
        CompoundTag b = peer == null ? null : data.anchor(peer.getUUID());
        if (!player.isAlive() || peer == null || !peer.isAlive() || b == null
                || !a.getUUID("Duel").equals(b.getUUID("Duel"))
                || !player.level().dimension().equals(DIMENSION) || peer.level() != player.level()
                || !peer.hasEffect(MagiaEffects.SOUL_REALM)) {
            onEffectEnded(player); return false;
        }
        double x = a.getDouble("ArenaX"), z = a.getDouble("ArenaZ");
        if (!validHorizontal(x, z)) { onEffectEnded(player); return false; }
        if (Math.abs(player.getX() - x) > 14 || Math.abs(player.getZ() - z) > 14
                || player.getY() < 65 || player.getY() > 76) {
            player.connection.teleport(Math.max(x - 13, Math.min(x + 13, player.getX())), 65,
                    Math.max(z - 13, Math.min(z + 13, player.getZ())), player.getYRot(), player.getXRot());
            player.resetFallDistance();
        }
        if (player.tickCount % 40 == 0) {
            MagiaNetwork.sendVisual(player, player, SpellVisualPayload.Kind.SOUL_REALM_AURA, 45);
            AurorionSpell.sound(player, SoundEvents.SOUL_ESCAPE.value(), .6F, .5F);
        }
        return true;
    }

    public static boolean empowered(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !player.hasEffect(MagiaEffects.SOUL_REALM)) return false;
        CompoundTag anchor = SoulRealmData.get(player.server).anchor(player.getUUID());
        return anchor != null && anchor.getBoolean("Owner");
    }

    /** As ancoras saem antes dos efeitos, tornando remocao/expiracao reentrantes. */
    public static void end(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        SoulRealmData data = SoulRealmData.get(player.server);
        CompoundTag a = data.anchor(player.getUUID());
        if (a == null) return;
        ServerPlayer peer = player.server.getPlayerList().getPlayer(a.getUUID("Peer"));
        CompoundTag b = peer == null ? null : data.anchor(peer.getUUID());
        if (peer != player && b != null && a.getUUID("Duel").equals(b.getUUID("Duel"))) restore(peer, b);
        restore(player, a);
    }

    /** Expiracao ocorre dentro da iteracao de efeitos. O retorno e a limpeza ficam fora dela. */
    public static void onEffectEnded(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !active(player)) return;
        player.server.tell(new TickTask(player.server.getTickCount(), () -> {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player
                    && !player.hasEffect(MagiaEffects.SOUL_REALM)) end(player);
        }));
    }

    private static boolean validHorizontal(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z) && Math.abs(x) < 30_000_000 && Math.abs(z) < 30_000_000;
    }

    private static void restore(ServerPlayer player, CompoundTag a) {
        restore(player, a, false);
    }

    private static void restore(ServerPlayer player, CompoundTag a, boolean dying) {
        // Morto ainda sera clonado pelo vanilla. Guardar a ancora permite o retorno no respawn.
        if (!player.isAlive() && !dying) return;
        SoulRealmData.get(player.server).remove(player.getUUID());
        player.removeEffect(MagiaEffects.SOUL_REALM);
        if (!player.level().dimension().equals(DIMENSION)) return;
        // Ancoras criadas dentro da arena nao pertencem ao mundo para onde o jogador volta.
        TimeStop.stop(player);
        player.removeEffect(MagiaEffects.BOUND);
        player.removeEffect(MagiaEffects.CAGED);
        player.removeEffect(MagiaEffects.INFINITE_VOID);
        player.stopRiding();
        ResourceLocation id = ResourceLocation.tryParse(a.getString("Dim"));
        ServerLevel home = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        Vec3 to = new Vec3(a.getDouble("X"), a.getDouble("Y"), a.getDouble("Z"));
        if (home == null || home.dimension().equals(DIMENSION) || !Double.isFinite(to.lengthSqr())
                || !validHorizontal(to.x, to.z) || Math.abs(to.y) >= 30_000_000) {
            home = player.server.overworld();
            to = Vec3.atBottomCenterOf(home.getSharedSpawnPos());
        }
        ServerLevel destination = home;
        Vec3 original = to;
        float yaw = Float.isFinite(a.getFloat("Yaw")) ? a.getFloat("Yaw") : 0;
        float pitch = Float.isFinite(a.getFloat("Pitch")) ? a.getFloat("Pitch") : 0;
        try {
            Vec3 landing = safeLanding(destination, original);
            TwilightTeleportCompat.immediateRun(() -> player.teleportTo(destination, landing.x, landing.y, landing.z,
                    Set.of(), yaw, pitch));
        } catch (RuntimeException failure) {
            AurorionMagia.LOGGER.error("Falha ao devolver {} do Reino das Almas; a ancora sera preservada.",
                    player.getUUID(), failure);
        } finally {
            // Um veto de outro mod nao pode apagar o unico registro de retorno.
            if (player.level().dimension().equals(DIMENSION)) SoulRealmData.get(player.server).restoreAnchor(player.getUUID(), a);
        }
        player.resetFallDistance();
        AurorionSpell.sound(player, SoundEvents.BEACON_DEACTIVATE, 1.5F, .65F);
    }

    /** Antes de Vidas/Limbo registrarem a morte, devolve o corpo sem alterar sua saude. */
    public static void onDeath(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        CompoundTag anchor = SoulRealmData.get(player.server).anchor(player.getUUID());
        if (anchor == null) return;
        end(player);
        // Os drops e o registro de espolio pertencem ao mundo de origem, nao a uma arena temporaria.
        restore(player, anchor, true);
    }

    private static Vec3 safeLanding(ServerLevel home, Vec3 original) {
        BlockPos at = BlockPos.containing(original);
        // Se alguem construiu na origem durante o duelo, procura espaco vertical sem cavar o mapa.
        for (int y = 0; y <= 12; y++) {
            BlockPos feet = at.above(y);
            if (home.getBlockState(feet).getCollisionShape(home, feet).isEmpty()
                    && home.getBlockState(feet.above()).getCollisionShape(home, feet.above()).isEmpty())
                return y == 0 ? original : Vec3.atBottomCenterOf(feet);
        }
        return Vec3.atBottomCenterOf(home.getSharedSpawnPos());
    }

    public static void recover(ServerPlayer player) {
        end(player);
        player.removeEffect(MagiaEffects.SOUL_REALM);
        // /tp da staff ou NBT sem ancora: nao fica perdido no vazio.
        if (player.level().dimension().equals(DIMENSION) && !active(player)) {
            // Registra ate o retorno de emergencia: se outro mod vetar, a proxima tentativa e segura.
            SoulRealmData data = SoulRealmData.get(player.server);
            data.remember(player, player, UUID.randomUUID(), player.position(), false);
            restore(player, data.anchor(player.getUUID()));
        }
    }
}

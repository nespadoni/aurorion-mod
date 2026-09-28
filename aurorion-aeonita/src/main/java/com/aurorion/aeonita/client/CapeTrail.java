package com.aurorion.aeonita.client;

import com.aurorion.aeonita.AurorionAeonita;
import com.aurorion.aeonita.registry.AeonitaItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * O rastro de quem veste a capa destruida dos Desvinculados: fumaça preta rala saindo da barra da
 * capa e os esporos azuis da Floresta Distorcida do Nether flutuando atras. Pouca coisa de proposito —
 * e marca de quem passou, e nao nuvem.
 *
 * <p>Inteiramente local: cada cliente olha os jogadores que ja conhece e desenha o rastro de quem esta
 * de capa. Nada vai para o servidor e nenhum pacote existe para isto.
 *
 * <p>Custo, pensado para a praça cheia (~90 pessoas): uma volta na lista de jogadores do cliente a cada
 * {@value #INTERVAL} ticks, com uma leitura de slot por jogador; so quem esta de capa e a menos de
 * {@value #MAX_DISTANCE} blocos da camera gera particula, no maximo tres por vez. Respeita a opcao
 * "Particulas" do vanilla, como os visuais de magia.
 */
@EventBusSubscriber(modid = AurorionAeonita.MOD_ID, value = Dist.CLIENT)
public final class CapeTrail {
    private static final int INTERVAL = 2;
    private static final double MAX_DISTANCE = 32;
    /** Parado, a capa quase nao solta nada: um fiapo a cada tantos ciclos. */
    private static final int IDLE_CHANCE = 8;
    private static final float MOVING = 0.1f;
    private static final ParticleOptions SMOKE = new DustParticleOptions(new Vector3f(0.02f, 0.0f, 0.03f), 1.3f);

    private CapeTrail() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || level.getGameTime() % INTERVAL != 0) return;

        int stride = switch (minecraft.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> 2;
            case MINIMAL -> 4;
        };
        Item cape = AeonitaItems.DESVINCULADOS_CAPE.get();
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        RandomSource random = level.random;

        for (AbstractClientPlayer player : level.players()) {
            if (!player.getItemBySlot(EquipmentSlot.CHEST).is(cape)) continue;
            if (player.isInvisible() || player.isSpectator()) continue;
            if (player.position().distanceToSqr(eye) > MAX_DISTANCE * MAX_DISTANCE) continue;
            // Em primeira pessoa o rastro nasceria dentro da camera de quem veste; o dos outros basta.
            if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) continue;

            boolean moving = player.walkAnimation.speed() > MOVING;
            if (!moving && random.nextInt(IDLE_CHANCE) != 0) continue;
            if (random.nextInt(stride) != 0) continue;
            trail(level, player, random, moving);
        }
    }

    private static void trail(ClientLevel level, AbstractClientPlayer player, RandomSource random, boolean moving) {
        // A barra da capa: atras do corpo, na altura das pernas.
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double behindX = Mth.sin(yaw) * 0.35;
        double behindZ = -Mth.cos(yaw) * 0.35;
        double x = player.getX() + behindX + (random.nextDouble() - 0.5) * 0.4;
        double z = player.getZ() + behindZ + (random.nextDouble() - 0.5) * 0.4;
        double y = player.getY() + 0.15 + random.nextDouble() * 0.7;

        level.addParticle(SMOKE, x, y, z, 0, 0.01, 0);
        if (moving && random.nextInt(2) == 0) {
            level.addParticle(SMOKE, x, player.getY() + 0.05, z, 0, 0.005, 0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.WARPED_SPORE, x, y + 0.3, z, 0, 0, 0);
        }
    }
}

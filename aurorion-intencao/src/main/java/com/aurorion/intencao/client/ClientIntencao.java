package com.aurorion.intencao.client;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.network.ScenePayload;
import com.aurorion.intencao.network.Tone;
import com.aurorion.intencao.network.WorldPayload;
import com.aurorion.intencao.server.Plane;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tudo que a batalha mostra, do lado do cliente. O servidor so diz <i>o que</i> aconteceu (chamado,
 * chegada, clima, frase, veredito, volta); tempo de tela, particula e som sao decididos aqui.
 *
 * <p>As particulas so nascem perto de quem esta olhando e em orcamento fixo por tick — uma batalha
 * com oito pessoas custa algumas dezenas de particulas por segundo, nao centenas.
 */
@EventBusSubscriber(modid = AurorionIntencao.MOD_ID, value = Dist.CLIENT)
public final class ClientIntencao {
    /** Duracao do chamado na tela; o teleporte vem no fim (mesmo valor do servidor). */
    static final int CALL_TICKS = Plane.CALL_TICKS;
    static final int VERDICT_TICKS = Plane.VERDICT_TICKS;
    static final int OPEN_TICKS = 30;
    /** Sem cena de chegada nesse tempo (servidor recusou, pacote perdido), a cortina abre sozinha. */
    private static final int CURTAIN_GIVE_UP = 200;
    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * @param heading o titulo do HUD com o nome do tema, e {@code names} os participantes coloridos —
     *                montados uma vez na chegada, e nao a cada quadro.
     */
    record Scene(List<ScenePayload.Member> members, int self, BlockPos center, int fog, ResourceLocation ambience,
                 ResourceLocation whisper, long startTick, Component heading, Component names) {
        int color(int index) {
            return index >= 0 && index < members.size() ? members.get(index).color() : 0xFFFFFF;
        }
    }

    record Moment(List<ScenePayload.Member> members, int self, String text, long start, ScenePayload.Outcome outcome) {
    }

    private record Visual(WorldPayload.Kind kind, int entity, Vec3 pos, int color, long start, int ttl) {
    }

    static long ticks;
    @Nullable static Scene scene;
    static Tone tone = Tone.CALMO;
    @Nullable static Moment call;
    @Nullable static Moment verdict;
    @Nullable static Moment phrase;
    /** Cortina (palpebras): fecha no fim do chamado e do veredito, abre ao chegar e ao voltar. */
    static long closeStart = -1;
    static int closeTicks = 20;
    static long openStart = -1;
    private static long kneelUntil;
    private static long nextWhisper;
    private static long nextAmbienceTry;
    private static long notInPlaneSince = -1;
    @Nullable private static AmbienceSound ambience;
    private static final Map<Integer, Visual> AURAS = new HashMap<>();
    private static final List<Visual> SPOTS = new ArrayList<>();

    private ClientIntencao() {
    }

    // --- Pacotes -------------------------------------------------------------------------------

    public static void accept(ScenePayload payload) {
        switch (payload.kind()) {
            case CALL -> {
                call = new Moment(payload.members(), payload.self(), payload.text(), ticks, null);
                verdict = null;
                closeStart = ticks + CALL_TICKS - 20;
                closeTicks = 20;
                openStart = -1;
            }
            case PLANE -> {
                MutableComponent names = Component.empty();
                for (int i = 0; i < payload.members().size(); i++) {
                    ScenePayload.Member member = payload.members().get(i);
                    if (i > 0) names.append(Component.literal("  ×  ").withStyle(ChatFormatting.DARK_GRAY));
                    names.append(Component.literal(member.name()).withColor(member.color()));
                }
                scene = new Scene(payload.members(), payload.self(), payload.center(), payload.fog(),
                        parse(payload.ambience(), "ambient.soul_sand_valley.loop"),
                        parse(payload.whisper(), "ambient.soul_sand_valley.mood"),
                        ticks - payload.elapsed() * 20L,
                        Component.translatable("aurorion_intencao.hud.titulo", Component.translatable(payload.text())),
                        names);
                tone = Tone.byId(payload.value());
                nextWhisper = ticks + 200 + RANDOM.nextInt(300);
                notInPlaneSince = -1;
                open();
            }
            case TONE -> tone = Tone.byId(payload.value());
            case PHRASE -> {
                phrase = new Moment(List.of(), -1, payload.text(), ticks, null);
                if (scene != null) play(SoundEvent.createVariableRangeEvent(scene.whisper()), 1F, .8F);
            }
            case VERDICT -> {
                ScenePayload.Outcome outcome = ScenePayload.Outcome.values()[Math.floorMod(payload.value(), 3)];
                verdict = new Moment(payload.members(), payload.self(), payload.text(), ticks, outcome);
                phrase = null;
                closeStart = ticks + VERDICT_TICKS - 15;
                closeTicks = 15;
                openStart = -1;
                switch (outcome) {
                    case WIN -> {
                        play(SoundEvents.BEACON_POWER_SELECT, 1.3F, 1F);
                        play(SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, 1F);
                    }
                    case LOSE -> {
                        play(SoundEvents.GLASS_BREAK, .6F, 1F);
                        play(SoundEvents.WARDEN_HEARTBEAT, .5F, 1F);
                    }
                    case DRAW -> {
                        play(SoundEvents.FIRE_EXTINGUISH, .5F, .8F);
                        play(SoundEvents.SOUL_ESCAPE.value(), .6F, 1F);
                    }
                }
            }
            case LEAVE -> {
                if (verdict != null && verdict.outcome() == ScenePayload.Outcome.LOSE) kneelUntil = ticks + 60;
                scene = null;
                call = null;
                tone = Tone.CALMO;
                open();
            }
        }
    }

    public static void accept(WorldPayload payload) {
        Visual visual = new Visual(payload.kind(), payload.entity(), payload.pos(), payload.color(), ticks, payload.ttl());
        if (payload.kind() == WorldPayload.Kind.AURA) AURAS.put(payload.entity(), visual);
        else if (SPOTS.size() < 32 && !duplicate(visual)) SPOTS.add(visual);
    }

    /** A onda de quem volta chega duas vezes para ele (direto e pelo chunk): desenha uma so. */
    private static boolean duplicate(Visual visual) {
        for (Visual spot : SPOTS) {
            if (spot.kind() == visual.kind() && ticks - spot.start() < 5 && spot.pos().distanceToSqr(visual.pos()) < .01) {
                return true;
            }
        }
        return false;
    }

    private static void open() {
        // So abre se havia cortina fechando/fechada; um relog no plano nao pisca a tela.
        if (closeStart >= 0 && ticks >= closeStart) openStart = ticks;
    }

    private static ResourceLocation parse(String raw, String fallback) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id != null ? id : ResourceLocation.withDefaultNamespace(fallback);
    }

    // --- Consultas para camadas e som ---------------------------------------------------------

    static boolean inPlane() {
        Minecraft minecraft = Minecraft.getInstance();
        return scene != null && minecraft.level != null && minecraft.level.dimension().equals(Plane.DIMENSION);
    }

    /** 0 aberta, 1 fechada. */
    static float curtain(float partial) {
        if (closeStart < 0) return 0;
        float now = ticks + partial;
        if (openStart >= closeStart) {
            float opened = Mth.clamp((now - openStart) / OPEN_TICKS, 0, 1);
            if (opened >= 1) {
                closeStart = -1;
                openStart = -1;
            }
            return 1 - opened;
        }
        if (now < closeStart) return 0;
        if (now - closeStart > CURTAIN_GIVE_UP) {
            openStart = ticks;
            return 1;
        }
        return Mth.clamp((now - closeStart) / closeTicks, 0, 1);
    }

    static float ambienceVolume() {
        if (!inPlane()) return 0;
        return switch (tone) {
            case CALMO -> .55F;
            case TENSO -> .8F;
            case RUPTURA -> 1F;
        };
    }

    static float ambiencePitch() {
        return switch (tone) {
            case CALMO -> 1F;
            case TENSO -> .88F;
            case RUPTURA -> .75F;
        };
    }

    /** Distancia da neblina no plano, por clima. */
    private static float fogFar() {
        return switch (tone) {
            case CALMO -> 56F;
            case TENSO -> 34F;
            case RUPTURA -> 20F;
        };
    }

    static float screenFogStrength() {
        return switch (tone) {
            case CALMO -> .3F;
            case TENSO -> .5F;
            case RUPTURA -> .7F;
        };
    }

    // --- Tick ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        ticks++;
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return;

        if (call != null) {
            long t = ticks - call.start();
            if (t < CALL_TICKS) {
                // Batimento que acelera ate o teleporte.
                int interval = Math.max(8, 22 - (int) (t / 7));
                if (t % interval == 0) play(SoundEvents.WARDEN_HEARTBEAT, .9F + t / 300F, .9F);
            } else if (t > CALL_TICKS + CURTAIN_GIVE_UP) {
                call = null;
            }
        }
        if (verdict != null && ticks - verdict.start() > VERDICT_TICKS + 60) verdict = null;
        if (phrase != null && ticks - phrase.start() > 110) phrase = null;

        if (scene != null) {
            if (level.dimension().equals(Plane.DIMENSION)) {
                notInPlaneSince = -1;
                tickPlane(minecraft, player, level);
            } else {
                if (notInPlaneSince < 0) notInPlaneSince = ticks;
                // A volta sempre manda LEAVE; isto so cobre um pacote perdido.
                if (ticks - notInPlaneSince > 100) scene = null;
            }
        }
        tickVisuals(level);
    }

    private static void tickPlane(Minecraft minecraft, LocalPlayer player, ClientLevel level) {
        Scene current = scene;
        if (current == null) return;
        // Um som que nao chega a tocar (jogo mudo, id de tema errado, sem canal livre num modpack pesado)
        // nunca fica "ativo": sem o intervalo, nasceria uma instancia nova a cada tick.
        if ((ambience == null || ambience.isStopped() || !minecraft.getSoundManager().isActive(ambience))
                && ticks >= nextAmbienceTry) {
            nextAmbienceTry = ticks + 40;
            ambience = new AmbienceSound(current.ambience());
            minecraft.getSoundManager().play(ambience);
        }
        if (ticks >= nextWhisper) {
            nextWhisper = ticks + 400 + RANDOM.nextInt(600);
            Vec3 at = player.position().add((RANDOM.nextDouble() - .5) * 12, 1, (RANDOM.nextDouble() - .5) * 12);
            minecraft.getSoundManager().play(new SimpleSoundInstance(SoundEvent.createVariableRangeEvent(current.whisper()),
                    SoundSource.AMBIENT, .5F, .9F + RANDOM.nextFloat() * .2F, RANDOM, at.x, at.y, at.z));
        }
        if (tone != Tone.CALMO && ticks % (tone == Tone.RUPTURA ? 16 : 28) == 0) {
            play(SoundEvents.WARDEN_HEARTBEAT, tone == Tone.RUPTURA ? .7F : .4F, .85F);
        }

        int n = Math.max(1, current.members().size());
        BlockPos c = current.center();
        // Os lados: poeira na cor de cada Casa, no ar sobre a metade dela.
        if (ticks % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2, distance = 2 + RANDOM.nextDouble() * 11;
                double x = Math.cos(angle) * distance, z = Math.sin(angle) * distance;
                int side = Plane.side(x, z, n);
                level.addParticle(dust(current.color(side), 1.1F), c.getX() + .5 + x, c.getY() + .2 + RANDOM.nextDouble() * 3,
                        c.getZ() + .5 + z, 0, .01, 0);
            }
        }
        // Fios entre os rivais e o rastro de luz de cada um.
        Player self = player;
        for (int i = 0; i < current.members().size(); i++) {
            ScenePayload.Member member = current.members().get(i);
            Player other = level.getPlayerByUUID(member.id());
            if (other == null) continue;
            double moved = Mth.square(other.getX() - other.xo) + Mth.square(other.getZ() - other.zo);
            if (moved > .0004 && other.onGround()) {
                level.addParticle(dust(member.color(), .7F), other.getX(), other.getY() + .05, other.getZ(), 0, .005, 0);
            }
            if (i != current.self() && ticks % 4 == 0 && other.distanceToSqr(self) < 40 * 40) {
                thread(level, self.position().add(0, 1.1, 0), other.position().add(0, 1.1, 0),
                        current.color(current.self()), member.color());
            }
        }
    }

    private static void thread(ClientLevel level, Vec3 from, Vec3 to, int fromColor, int toColor) {
        for (int k = 0; k < 6; k++) {
            float t = RANDOM.nextFloat();
            Vec3 at = from.lerp(to, t);
            // Um arco leve: o fio pende no meio, como uma corda.
            double sag = Math.sin(t * Math.PI) * .6;
            level.addParticle(dust(lerp(fromColor, toColor, t), .55F), at.x, at.y - sag, at.z, 0, 0, 0);
        }
    }

    private static void tickVisuals(ClientLevel level) {
        for (Iterator<Visual> it = AURAS.values().iterator(); it.hasNext(); ) {
            Visual aura = it.next();
            Entity entity = level.getEntity(aura.entity());
            if (entity == null || ticks - aura.start() > aura.ttl()) {
                it.remove();
                continue;
            }
            if (ticks % 2 == 0) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                level.addParticle(dust(aura.color(), 1F), entity.getX() + Math.cos(angle) * .6,
                        entity.getY() + RANDOM.nextDouble() * entity.getBbHeight(), entity.getZ() + Math.sin(angle) * .6, 0, .02, 0);
            }
            if (ticks % 10 == 0) {
                level.addParticle(ParticleTypes.END_ROD, entity.getX(), entity.getY() + entity.getBbHeight() + .2, entity.getZ(),
                        0, .04, 0);
            }
        }
        for (Iterator<Visual> it = SPOTS.iterator(); it.hasNext(); ) {
            Visual spot = it.next();
            long t = ticks - spot.start();
            if (t > spot.ttl()) {
                it.remove();
                continue;
            }
            Vec3 p = spot.pos();
            if (spot.kind() == WorldPayload.Kind.SEAL) {
                // Selo no chao e o puxao para cima: as particulas sobem cada vez mais rapido.
                if (t % 2 == 0) ring(level, p.add(0, .05, 0), 1.2, 12, spot.color(), 0);
                for (int k = 0; k < 2; k++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2;
                    level.addParticle(dust(spot.color(), .9F), p.x + Math.cos(angle) * 1.2, p.y + .1,
                            p.z + Math.sin(angle) * 1.2, -Math.cos(angle) * .03, .05 + t * .003, -Math.sin(angle) * .03);
                }
            } else if (spot.kind() == WorldPayload.Kind.WAVE && t < 20) {
                ring(level, p.add(0, .1, 0), .5 + t * .35, 20, spot.color(), .01);
                if (t == 0) {
                    for (int k = 0; k < 12; k++) {
                        level.addParticle(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, (RANDOM.nextDouble() - .5) * .3,
                                RANDOM.nextDouble() * .2, (RANDOM.nextDouble() - .5) * .3);
                    }
                }
            }
        }
    }

    private static void ring(ClientLevel level, Vec3 center, double radius, int points, int color, double up) {
        double offset = RANDOM.nextDouble();
        for (int k = 0; k < points; k++) {
            double angle = (k + offset) / points * Math.PI * 2;
            level.addParticle(dust(color, .8F), center.x + Math.cos(angle) * radius, center.y,
                    center.z + Math.sin(angle) * radius, 0, up, 0);
        }
    }


    static DustParticleOptions dust(int color, float size) {
        return new DustParticleOptions(new Vector3f(FastColor.ARGB32.red(color) / 255F,
                FastColor.ARGB32.green(color) / 255F, FastColor.ARGB32.blue(color) / 255F), size);
    }

    static int lerp(int a, int b, float t) {
        return FastColor.ARGB32.color(255,
                (int) Mth.lerp(t, FastColor.ARGB32.red(a), FastColor.ARGB32.red(b)),
                (int) Mth.lerp(t, FastColor.ARGB32.green(a), FastColor.ARGB32.green(b)),
                (int) Mth.lerp(t, FastColor.ARGB32.blue(a), FastColor.ARGB32.blue(b))) & 0xFFFFFF;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // --- Camera, neblina, corpo ----------------------------------------------------------------

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inPlane()) return;
        int fog = scene.fog();
        event.setRed(FastColor.ARGB32.red(fog) / 255F);
        event.setGreen(FastColor.ARGB32.green(fog) / 255F);
        event.setBlue(FastColor.ARGB32.blue(fog) / 255F);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!inPlane() || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = Math.min(event.getFarPlaneDistance(), fogFar());
        event.setNearPlaneDistance(far * .1F);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double t = ticks + event.getPartialTick();
        float strength = 0;
        if (inPlane() && tone == Tone.RUPTURA) strength = .8F;
        if (verdict != null && verdict.outcome() == ScenePayload.Outcome.LOSE) {
            float since = (float) (t - verdict.start());
            if (since < 14) strength = Math.max(strength, 2.2F * (1 - since / 14));
        }
        if (strength <= 0) return;
        event.setRoll((float) (event.getRoll() + Math.sin(t * 1.9) * .9 * strength));
        event.setPitch((float) (event.getPitch() + Math.cos(t * 2.3) * .5 * strength));
    }

    /** Perdedor volta agachado, de joelhos, por tres segundos. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (ticks < kneelUntil) event.getInput().shiftKeyDown = true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        scene = null;
        call = null;
        verdict = null;
        phrase = null;
        tone = Tone.CALMO;
        closeStart = -1;
        openStart = -1;
        kneelUntil = 0;
        AURAS.clear();
        SPOTS.clear();
        ambience = null;
    }
}

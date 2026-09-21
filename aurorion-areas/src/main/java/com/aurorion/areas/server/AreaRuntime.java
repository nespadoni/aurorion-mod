package com.aurorion.areas.server;

import com.aurorion.areas.api.AreaApi;
import com.aurorion.areas.compat.IronSpellsCompat;
import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.network.*;
import com.aurorion.areas.profile.*;
import com.aurorion.areas.rules.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import java.util.*;

/** All mutable state is server-thread confined; lookups reuse one result per connected player. */
public final class AreaRuntime {
    public static final ResourceKey<DamageType> PHANTOM = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.parse("aurorion_areas:phantom"));
    public static final ResourceKey<DamageType> PHANTOM_LETHAL = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.parse("aurorion_areas:phantom_lethal"));
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Component NO_FLIGHT = Component.literal("Uma força neste lugar impede seu voo.");
    private static final Component NO_MAGIC = Component.literal("A magia não responde neste lugar.");
    /** Meio bloco de diferenca ja se ve na tela; menos que isso so gastaria pacote. */
    private static final float FOG_STEP = .5F;
    /** A neblina so reaperta uma vez por segundo, por mais que o medo suba dentro do tick. */
    private static final int FOG_RESEND_TICKS = 20;
    /** Mesmo teto do {@code AreaClient}: acima disso a sombra periferica e cortada na chegada. */
    private static final float MAX_VIGNETTE = .9F;
    private AreaRuntime() {}

    /**
     * Visivel ao pacote porque o {@link HouseBarrier} guarda aqui a ultima posicao permitida do
     * jogador: e o mesmo estado por jogador, com o mesmo ciclo de vida, e um segundo mapa por UUID
     * so para a barreira seria uma segunda coisa para lembrar de limpar no logout.
     */
    static final class State {
        final ResolvedRules rules = new ResolvedRules();
        ResourceLocation dimension;
        double x = Double.NaN, y, z;
        long revision = -1, profileRevision = -1, soundAt, pulseAt, attackAt, noticeAt, whisperAt, spawnAt;
        AmbientProfile profile;
        boolean sent, flightBlocked, magicBlocked;
        long landingUntil;
        /** Ticks seguidos dentro do ambiente atual. Zera ao sair, ao trocar de ambiente e no reload. */
        long dreadTicks;
        int lastWhisper = -1;
        /** Neblina ja apertada pelo medo, e a ultima que o cliente recebeu. */
        float fogNow, sentFog;
        long fogSentAt = Long.MIN_VALUE;
        /**
         * Aviso a staff: {@code alertHandled} = a entrada ja foi processada nesta permanencia,
         * {@code alertAnnounced} = ela chegou a ser anunciada (e por isso a saida tambem deve ser).
         * Separados porque o silencio da carencia nao pode virar um "saiu" sem o "entrou".
         */
        boolean alertHandled, alertAnnounced, lethalAlerted;
        long alertCooldownUntil;
        Component alertArea;
        // Barreira de casa (ver HouseBarrier).
        double safeX, safeY, safeZ;
        boolean hasSafeSpot;
        long ejectAt, barrierNoticeAt;
    }
    private static State state(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) { state = new State(); STATES.put(player.getUUID(), state); }
        var data = AreaData.get(player.server);
        var dimension = player.level().dimension().location();
        if (state.revision != data.revision() || !dimension.equals(state.dimension)
                || state.x != player.getX() || state.y != player.getY() || state.z != player.getZ()) {
            state.x = player.getX(); state.y = player.getY(); state.z = player.getZ();
            state.dimension = dimension; state.revision = data.revision();
            data.resolve(dimension, state.x, state.y, state.z, player.getUUID(), state.rules);
        }
        return state;
    }
    public static ResolvedRules rules(ServerPlayer player) { return state(player).rules; }
    /**
     * Saida pela porta dos fundos: desconectar dentro de um ambiente que avisa a staff fecha o aviso
     * em vez de deixar a pessoa "la dentro" para sempre no historico de quem modera. Sair de um lugar
     * que pode matar e justamente o que alguem faria para escapar dele.
     */
    public static void logout(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null && state.alertAnnounced && state.profile != null && state.profile.alert().exit()) {
            StaffAlert.disconnected(player, state.alertArea, state.dreadTicks);
        }
        forget(player.getUUID());
    }
    public static void forget(UUID id) { STATES.remove(id); }
    public static void clear() { STATES.clear(); }

    public static void tick(ServerPlayer player) {
        State s = state(player);
        long now = player.serverLevel().getGameTime();
        boolean enabled = AreasConfig.ENABLED.get();
        boolean active = enabled && !AreaApi.bypass(player) && player.isAlive();
        // A barreira de casa tem bypass proprio (modo de jogo, nao permissao), entao ela nao pode
        // depender do 'active' acima — um administrador em sobrevivencia bate na parede como todo
        // mundo, e um administrador em criativo passa mesmo com o creativeStaffBypass desligado.
        if (enabled && AreasConfig.HOUSE_BARRIER.get() && player.isAlive() && !HouseBarrier.bypass(player)) {
            HouseBarrier.tick(player, s, now);
        }
        boolean flight = active && !s.rules.allows(AreaRule.FLIGHT);
        boolean magic = active && !s.rules.allows(AreaRule.MAGIC);
        AmbientProfile profile = active ? AreaProfiles.AMBIENCE.get(s.rules.ambience()) : null;
        boolean changed = !s.sent || s.flightBlocked != flight || s.profile != profile
                || s.profileRevision != AreaProfiles.revision();
        // Querying rules from another event must not consume the transition used to cancel a cast.
        if ((magic && !s.magicBlocked) || (flight && !s.flightBlocked)) IronSpellsCompat.stopForbiddenCast(player, magic, flight);
        s.magicBlocked = magic; s.flightBlocked = flight;
        boolean ambienceChanged = s.profile != profile;
        if (ambienceChanged || s.profileRevision != AreaProfiles.revision()) {
            // O aviso de saida usa o ambiente e o nome de lugar antigos, entao vem antes da troca.
            // Um /reload nao dispara nada: a pessoa nao saiu de lugar nenhum.
            if (ambienceChanged && s.alertAnnounced && s.profile != null && s.profile.alert().exit()) {
                StaffAlert.left(player, s.alertArea, s.dreadTicks);
            }
            s.profile = profile; s.profileRevision = AreaProfiles.revision();
            // Sair zera o medo: quem entra de novo recomeca do sussurro mais manso, nao do auge.
            s.dreadTicks = 0; s.lastWhisper = -1; s.lethalAlerted = false;
            if (ambienceChanged) { s.alertHandled = false; s.alertAnnounced = false; s.alertArea = null; }
            if (profile != null) {
                s.soundAt = profile.sounds().interval().next(now, player.getRandom());
                s.pulseAt = profile.pulse().interval().next(now, player.getRandom());
                s.attackAt = profile.attack().interval().next(now, player.getRandom());
                s.whisperAt = profile.whispers().interval().next(now, player.getRandom());
                // A primeira leva de criaturas espera o silencio inicial; entrar e sair nao povoa nada.
                s.spawnAt = profile.spawns().interval().next(
                        now + 20L * profile.spawns().startAfterSeconds(), player.getRandom());
            }
        }
        // O medo cresce antes de qualquer envio: a neblina do pacote desta tick ja e a apertada.
        float dread = 0;
        if (profile == null) {
            s.fogNow = 0;
        } else {
            s.dreadTicks++;
            dread = profile.dread().level(s.dreadTicks);
            s.fogNow = profile.fog().distance() * profile.dread().ramp(profile.dread().fogScale(), dread);
        }
        if (changed) { send(player, s, 0, 0, 0, 0); s.sent = true; }
        if (flight) enforceFlight(player, s, now);
        // A bounded landing grace prevents cancelling flight at a border from killing a character.
        // No ability/effect from another mod is restored or removed when leaving.
        if (s.landingUntil > now && !player.onGround()) player.fallDistance = 0;
        else s.landingUntil = 0;
        if (profile == null) return;
        // A neblina fecha aos poucos; reenviar so no degrau evita um pacote por tick por jogador.
        if (Math.abs(s.fogNow - s.sentFog) >= FOG_STEP && now >= s.fogSentAt + FOG_RESEND_TICKS) {
            send(player, s, 0, 0, 0, 0);
        }
        if (!s.alertHandled) {
            s.alertHandled = true;
            var area = s.rules.ambienceArea();
            s.alertArea = Component.literal(area == null ? profile.id().toString() : area.name());
            if (profile.alert().enter() && now >= s.alertCooldownUntil) {
                s.alertCooldownUntil = now + 20L * profile.alert().cooldownSeconds();
                s.alertAnnounced = true;
                StaffAlert.entered(player, s.alertArea);
            }
        }
        if (!s.lethalAlerted && profile.alert().lethal() && profile.dread().lethalNow(s.dreadTicks)) {
            s.lethalAlerted = true;
            StaffAlert.lethal(player, s.alertArea, s.dreadTicks);
        }
        float pace = profile.dread().ramp(profile.dread().intervalScale(), dread);
        if (now >= s.soundAt) {
            s.soundAt = profile.sounds().interval().next(now, player.getRandom(), pace);
            sound(player, profile.sounds());
        }
        if (now >= s.pulseAt) {
            var pulse = profile.pulse();
            s.pulseAt = pulse.interval().next(now, player.getRandom(), pace);
            int darkness = pulse.darknessSeconds() + Math.round(profile.dread().darknessBonus() * dread);
            int blindness = pulse.blindnessSeconds() + Math.round(profile.dread().blindnessBonus() * dread);
            addPulse(player, MobEffects.DARKNESS, darkness);
            addPulse(player, MobEffects.BLINDNESS, blindness);
            // 0,9 e o teto que o AreaClient aplica ao receber; somar mais aqui so mandaria um numero
            // que o cliente descarta, e faria a tabela do README prometer algo que nao acontece.
            float vignette = Math.min(MAX_VIGNETTE, pulse.vignette() + profile.dread().vignetteBonus() * dread);
            // O apagao nasce do medo, e nao so cresce com ele: na entrada e zero.
            float blackout = pulse.blackout() * dread;
            int pulseTicks = 20 * Math.max(3, Math.max(darkness, blindness));
            if (vignette > 0 || blackout > 0) {
                send(player, s, vignette, blackout, pulseTicks,
                        blackout > 0 ? Math.min(pulseTicks, 20 * pulse.blackoutSeconds()) : 0);
            }
        }
        if (now >= s.attackAt) {
            var attack = profile.attack();
            s.attackAt = attack.interval().next(now, player.getRandom(), pace);
            float damage = attack.damage() * profile.dread().ramp(profile.dread().damageScale(), dread);
            boolean lethal = attack.lethal() || profile.dread().lethalNow(s.dreadTicks);
            if (damage > 0 && (lethal || player.getHealth() > 1)) {
                var key = lethal ? PHANTOM_LETHAL : PHANTOM;
                var type = player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
                // A datapack may remove a damage type; do not crash the server tick.
                type.ifPresent(holder -> player.hurt(new DamageSource(holder), damage));
            }
        }
        if (now >= s.whisperAt) {
            s.whisperAt = profile.whispers().interval().next(now, player.getRandom(), pace);
            whisper(player, s, profile.whispers(), dread);
        }
        if (now >= s.spawnAt) {
            s.spawnAt = profile.spawns().interval().next(now, player.getRandom(), pace);
            DreadSpawner.spawnWave(player, profile.spawns(), dread);
        }
    }
    private static void enforceFlight(ServerPlayer p, State s, long now) {
        boolean stopped = false;
        if (p.getAbilities().flying) { p.getAbilities().flying = false; p.onUpdateAbilities(); stopped = true; }
        if (p.isFallFlying()) { p.stopFallFlying(); stopped = true; }
        if (AreaTags.isFlightMount(p.getVehicle())) {
            p.stopRiding(); stopped = true;
        }
        // Only effects explicitly classified as flight are dispelled; NPCs never enter this path.
        // Removal is outside iteration and at most one effect per tick (normally zero).
        Holder<MobEffect> remove = null;
        if (p.tickCount % 10 == 0) {
            for (var effect : p.getActiveEffects()) {
                if (effect.getEffect().is(AreaTags.FLIGHT_EFFECTS)) { remove = effect.getEffect(); break; }
            }
        }
        if (remove != null) { p.removeEffect(remove); stopped = true; }
        if (stopped) {
            if (!p.onGround()) s.landingUntil = now + 200;
            if (!p.onGround() && !p.hasEffect(MobEffects.SLOW_FALLING))
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, false));
            denyNotice(p, AreaRule.FLIGHT);
        }
    }
    private static void addPulse(ServerPlayer player, Holder<MobEffect> effect, int seconds) {
        // Never overwrite an existing potion or remove somebody else's effect on exit.
        if (seconds > 0 && !player.hasEffect(effect))
            player.addEffect(new MobEffectInstance(effect, seconds * 20, 0, false, false, false));
    }
    private static void sound(ServerPlayer player, AmbientProfile.Sounds sounds) {
        if (sounds.events().isEmpty()) return;
        var random = player.getRandom();
        var id = sounds.events().get(random.nextInt(sounds.events().size()));
        var event = BuiltInRegistries.SOUND_EVENT.getOptional(id);
        if (event.isEmpty()) return;
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 2 + random.nextDouble() * (sounds.distance() - 2);
        player.connection.send(new ClientboundSoundPacket(Holder.direct(event.get()), SoundSource.AMBIENT,
                player.getX() + Math.cos(angle) * distance, player.getY() + .5,
                player.getZ() + Math.sin(angle) * distance, sounds.volume(),
                sounds.pitch(), random.nextLong()));
    }
    /**
     * A voz da propria pessoa, na barra de acao.
     *
     * <p>Nunca repete a frase anterior de seguida: a mesma linha duas vezes seguidas denuncia o
     * sorteio e quebra a ilusao de ser um pensamento.
     */
    private static void whisper(ServerPlayer player, State s, AmbientProfile.Whispers whispers, float dread) {
        int count = whispers.lines().size();
        if (count == 0) return;
        int index = player.getRandom().nextInt(count);
        if (count > 1 && index == s.lastWhisper) index = (index + 1) % count;
        s.lastWhisper = index;
        player.displayClientMessage(Component.literal(whispers.lines().get(index))
                .setStyle(Style.EMPTY.withColor(whispers.colorAt(dread)).withItalic(true)), true);
    }
    private static void send(ServerPlayer player, State s, float vignette, float blackout,
                             int ticks, int blackoutTicks) {
        var fog = s.profile == null ? AmbientProfile.Fog.NONE : s.profile.fog();
        AreaNetwork.send(player, new AreaStatePayload(s.dimension, s.fogNow, fog.color(),
                vignette, blackout, ticks, blackoutTicks, s.flightBlocked));
        s.sentFog = s.fogNow;
        s.fogSentAt = player.serverLevel().getGameTime();
    }
    public static void denyNotice(ServerPlayer player, AreaRule rule) {
        State s = state(player);
        long now = player.serverLevel().getGameTime();
        if (now < s.noticeAt) return;
        s.noticeAt = now + 60;
        player.displayClientMessage(rule == AreaRule.FLIGHT ? NO_FLIGHT : NO_MAGIC, true);
    }
}

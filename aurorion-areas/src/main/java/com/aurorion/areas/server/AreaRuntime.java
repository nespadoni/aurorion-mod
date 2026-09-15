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
    private AreaRuntime() {}
    private static final class State {
        final ResolvedRules rules = new ResolvedRules();
        ResourceLocation dimension;
        double x = Double.NaN, y, z;
        long revision = -1, profileRevision = -1, soundAt, pulseAt, attackAt, noticeAt;
        AmbientProfile profile;
        boolean sent, flightBlocked, magicBlocked;
        long landingUntil;
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
    public static void forget(UUID id) { STATES.remove(id); }
    public static void clear() { STATES.clear(); }

    public static void tick(ServerPlayer player) {
        State s = state(player);
        long now = player.serverLevel().getGameTime();
        boolean active = AreasConfig.ENABLED.get() && !AreaApi.bypass(player) && player.isAlive();
        boolean flight = active && !s.rules.allows(AreaRule.FLIGHT);
        boolean magic = active && !s.rules.allows(AreaRule.MAGIC);
        AmbientProfile profile = active ? AreaProfiles.AMBIENCE.get(s.rules.ambience()) : null;
        boolean changed = !s.sent || s.flightBlocked != flight || s.profile != profile
                || s.profileRevision != AreaProfiles.revision();
        // Querying rules from another event must not consume the transition used to cancel a cast.
        if ((magic && !s.magicBlocked) || (flight && !s.flightBlocked)) IronSpellsCompat.stopForbiddenCast(player, magic, flight);
        s.magicBlocked = magic; s.flightBlocked = flight;
        if (s.profile != profile || s.profileRevision != AreaProfiles.revision()) {
            s.profile = profile; s.profileRevision = AreaProfiles.revision();
            if (profile != null) {
                s.soundAt = profile.sounds().interval().next(now, player.getRandom());
                s.pulseAt = profile.pulse().interval().next(now, player.getRandom());
                s.attackAt = profile.attack().interval().next(now, player.getRandom());
            }
        }
        if (changed) { send(player, s, 0, 0); s.sent = true; }
        if (flight) enforceFlight(player, s, now);
        // A bounded landing grace prevents cancelling flight at a border from killing a character.
        // No ability/effect from another mod is restored or removed when leaving.
        if (s.landingUntil > now && !player.onGround()) player.fallDistance = 0;
        else s.landingUntil = 0;
        if (profile == null) return;
        if (now >= s.soundAt) {
            s.soundAt = profile.sounds().interval().next(now, player.getRandom());
            sound(player, profile.sounds());
        }
        if (now >= s.pulseAt) {
            var pulse = profile.pulse();
            s.pulseAt = pulse.interval().next(now, player.getRandom());
            addPulse(player, MobEffects.DARKNESS, pulse.darknessSeconds());
            addPulse(player, MobEffects.BLINDNESS, pulse.blindnessSeconds());
            if (pulse.vignette() > 0) send(player, s, pulse.vignette(),
                    20 * Math.max(3, Math.max(pulse.darknessSeconds(), pulse.blindnessSeconds())));
        }
        if (now >= s.attackAt) {
            var attack = profile.attack();
            s.attackAt = attack.interval().next(now, player.getRandom());
            if (attack.damage() > 0 && (attack.lethal() || player.getHealth() > 1)) {
                var key = attack.lethal() ? PHANTOM_LETHAL : PHANTOM;
                var type = player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
                // A datapack may remove a damage type; do not crash the server tick.
                type.ifPresent(holder -> player.hurt(new DamageSource(holder), attack.damage()));
            }
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
    private static void send(ServerPlayer player, State s, float vignette, int ticks) {
        var fog = s.profile == null ? AmbientProfile.Fog.NONE : s.profile.fog();
        AreaNetwork.send(player, new AreaStatePayload(s.dimension, fog.distance(), fog.color(), vignette, ticks, s.flightBlocked));
    }
    public static void denyNotice(ServerPlayer player, AreaRule rule) {
        State s = state(player);
        long now = player.serverLevel().getGameTime();
        if (now < s.noticeAt) return;
        s.noticeAt = now + 60;
        player.displayClientMessage(rule == AreaRule.FLIGHT ? NO_FLIGHT : NO_MAGIC, true);
    }
}

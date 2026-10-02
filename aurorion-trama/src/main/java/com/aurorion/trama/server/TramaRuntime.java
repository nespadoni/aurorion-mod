package com.aurorion.trama.server;

import com.aurorion.core.character.*;
import com.aurorion.core.house.HouseGate;
import com.aurorion.trama.config.TramaConfig;
import com.aurorion.trama.progression.*;
import com.aurorion.trama.skill.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.*;
import java.util.*;
import java.util.stream.Collectors;

public final class TramaRuntime {
    public static final ResourceLocation CATEGORY = ResourceLocation.parse("aurorion_trama:trama_do_eco");
    public static final ResourceLocation POINTS = ResourceLocation.parse("aurorion_trama:formacao");
    private static final Map<String,String> ORIGINS = Map.of("ignivar","I00","sylvara","S00","nyx","N00","venthra","V00","aetheris","A00");
    private static final Map<UUID,TramaState> STATES = new HashMap<>();
    private static final Set<UUID> LOGIN_PENDING = new HashSet<>();
    private TramaRuntime() {}
    public static void invalidate(UUID account) { var state = STATES.get(account); if (state != null) state.dirty = true; }
    public static void forget(UUID account) { STATES.remove(account); }
    public static void login(ServerPlayer p) { forget(p.getUUID()); LOGIN_PENDING.add(p.getUUID()); }
    public static void logout(UUID account) { forget(account); LOGIN_PENDING.remove(account); }
    public static void clear() { STATES.clear(); LOGIN_PENDING.clear(); }
    static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    public static boolean eligible(ServerPlayer p) {
        return !LOGIN_PENDING.contains(p.getUUID()) && TramaConfig.ENABLED.get() && p.isAlive() && !p.isSpectator() && !p.isCreative()
                && !CharacterData.get(p.server).isDead(p.getUUID())
                && (!CharacterGate.creationEnabled() || !CharacterData.get(p.server).needsName(p.getUUID()))
                && !CharacterGate.deferred(p) && origin(p) != null;
    }
    private static String origin(ServerPlayer p) {
        var house = HouseGate.of(p.server,p.getUUID());
        return house == null || !HouseGate.exists(house) || !house.getNamespace().equals("aurorion_ethereal")
                ? null : ORIGINS.get(house.getPath());
    }
    public static ProgressData.Entry entry(ServerPlayer p) {
        var data = ProgressData.get(p.server);
        var character = CharacterData.get(p.server).current(p.getUUID()).id();
        var entry = data.find(p.getUUID());
        if (entry == null || !entry.character.equals(character)) {
            SkillsAPI.getCategory(CATEGORY).ifPresent(c -> c.erase(p));
            TramaAttributes.apply(p,null); forget(p.getUUID());
            entry = data.replace(p.getUUID(),character);
        }
        return entry;
    }
    public static boolean sync(ServerPlayer p) {
        var category = SkillsAPI.getCategory(CATEGORY);
        if (category.isEmpty()) { TramaAttributes.apply(p,null); return false; }
        var c = category.get();
        // Login listeners from other mods must finish loading their data before reconciliation.
        if (LOGIN_PENDING.contains(p.getUUID())) return false;
        if (!TramaConfig.ENABLED.get() || CharacterData.get(p.server).isDead(p.getUUID())
                || CharacterGate.creationEnabled() && CharacterData.get(p.server).needsName(p.getUUID())) {
            if (c.isUnlocked(p)) c.lock(p);
            TramaAttributes.apply(p,null); invalidate(p.getUUID()); return false;
        }
        var entry = entry(p);
        if (entry.eraseCategory) {
            c.erase(p); entry.eraseCategory = false; ProgressData.get(p.server).setDirty();
            invalidate(p.getUUID());
        }
        var root = origin(p);
        if (root == null) {
            if (!entry.origin.isEmpty()) { c.resetSkills(p); entry.origin = ""; ProgressData.get(p.server).setDirty(); }
            if (c.isUnlocked(p)) c.lock(p);
            TramaAttributes.apply(p,null); invalidate(p.getUUID()); return false;
        }
        if (!root.equals(entry.origin)) {
            c.resetSkills(p); entry.origin = root; ProgressData.get(p.server).setDirty();
            invalidate(p.getUUID());
        }
        if (!c.isUnlocked(p)) c.unlock(p);
        // Repair origins after respec/admin reset/reload without granting any other root.
        for (String id : ORIGINS.values()) c.getSkill(id).ifPresent(skill -> {
            if (id.equals(root) && skill.getState(p) != Skill.State.UNLOCKED) skill.unlock(p);
            else if (!id.equals(root) && skill.getState(p) == Skill.State.UNLOCKED) skill.lock(p);
        });
        reconcile(p,c,entry);
        return true;
    }
    private static void reconcile(ServerPlayer p, Category c, ProgressData.Entry entry) {
        int earned = ProgressRules.earned(entry.experience,entry.activeSeconds,TramaConfig.XP_FIRST.get(),
                TramaConfig.XP_INCREMENT.get(),TramaConfig.MINUTES_FIRST.get(),TramaConfig.MINUTES_INCREMENT.get());
        // Config changes never revoke already-earned points; authoritative absolute source prevents login duplication.
        if (earned > entry.earned) { entry.earned = earned; ProgressData.get(p.server).setDirty(); }
        if (c.getPoints(p,POINTS) != entry.earned) c.setPoints(p,POINTS,entry.earned);
    }
    static TramaState state(ServerPlayer p) {
        var s = STATES.computeIfAbsent(p.getUUID(), id -> new TramaState(now(p)));
        if (s.dirty) {
            s.build = SkillsAPI.getCategory(CATEGORY).filter(c -> c.isUnlocked(p))
                    .map(c -> new Build(c.streamUnlockedSkills(p).map(Skill::getId).filter(SkillCatalog::contains).collect(Collectors.toSet())))
                    .orElseGet(() -> new Build(Set.of()));
            s.dirty = false;
        }
        s.sprint(p.isSprinting(),now(p));
        return s;
    }
    static Build.Context context(ServerPlayer p, TramaState s) {
        long time = now(p);
        double fraction = p.getHealth()/Math.max(1,p.getMaxHealth());
        boolean recovering = s.build.has("SG9") && time-s.lastCombat >= 240 && fraction < .90;
        return new Build.Context(fraction,p.isSprinting(),p.isShiftKeyDown(),!s.sprint && time-s.stoppedSince >= 40,
                !s.sprint && time-s.stoppedSince >= 40 && time-s.lastDamage >= 40,
                time < s.momentumUntil,time < s.evasionUntil,time < s.magicStepUntil,time < s.openingUntil,recovering);
    }
    public static void tick(ServerPlayer p) {
        // Sprint timers need transitions every tick; other work is staggered once per second.
        var previous = STATES.get(p.getUUID());
        if (previous != null) {
            boolean transition = previous.sprint != p.isSprinting();
            previous.sprint(p.isSprinting(),now(p));
            if (transition && eligible(p)) {
                var s = state(p); TramaAttributes.apply(p,s.build.effects(context(p,s)));
            }
        }
        if (Math.floorMod(p.server.getTickCount()+p.getId(),20) != 0) return;
        boolean login = LOGIN_PENDING.remove(p.getUUID());
        if (!eligible(p)) {
            TramaAttributes.apply(p,null);
            if (previous != null) previous.activity.clear();
            sync(p);
            return;
        }
        if (!sync(p)) return;
        if (login) backfill(p);
        var s = state(p);
        String dimension = p.level().dimension().location().toString();
        if (!s.dimension.equals(dimension)) { s.dimension = dimension; s.activity.clear(); }
        TramaAttributes.apply(p,s.build.effects(context(p,s)));
        heal(p,s);
        var entry = entry(p);
        if (entry.earned >= ProgressRules.MAX_POINTS) return;
        if (p.isPassenger() || p.getAbilities().flying) { s.activity.clear(); return; }
        if (s.activity.sample(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot())) {
            entry.activeSeconds += 60;
            long day = Math.floorDiv(System.currentTimeMillis(),86_400_000L);
            if (day > entry.practiceDay) { entry.practiceDay = day; entry.dailyPracticeXp = 0; }
            int xp = Math.max(0,Math.min(TramaConfig.PRACTICE_XP.get(),TramaConfig.PRACTICE_DAILY_CAP.get()-entry.dailyPracticeXp));
            entry.dailyPracticeXp += xp; entry.experience += xp;
            ProgressData.get(p.server).setDirty();
            biome(p,entry);
            reconcile(p,SkillsAPI.getCategory(CATEGORY).orElseThrow(),entry);
        }
    }
    private static void biome(ServerPlayer p, ProgressData.Entry entry) {
        if (entry.biomes.size() >= TramaConfig.BIOME_LIMIT.get()) return;
        p.level().getBiome(p.blockPosition()).unwrapKey().ifPresent(key -> {
            if (entry.biomes.add(key.location().toString())) {
                entry.experience += TramaConfig.BIOME_XP.get(); ProgressData.get(p.server).setDirty();
                p.sendSystemMessage(Component.literal("Trama: novo bioma, +"+TramaConfig.BIOME_XP.get()+" XP de formação."));
            }
        });
    }
    public static void advancement(ServerPlayer p, ResourceLocation id) {
        if (!eligible(p) || !TramaConfig.ADVANCEMENTS.get().contains(id.toString())) return;
        var entry = entry(p);
        if (entry.earned == 45 || !entry.advancements.add(id.toString())) return;
        entry.experience += TramaConfig.ADVANCEMENT_XP.get(); ProgressData.get(p.server).setDirty();
        sync(p);
        p.sendSystemMessage(Component.literal("Trama: conquista inédita, +"+TramaConfig.ADVANCEMENT_XP.get()+" XP de formação."));
    }
    public static void backfill(ServerPlayer p) {
        if (!eligible(p)) return;
        var entry = entry(p);
        if (entry.earned == 45) return;
        long granted = 0;
        boolean changed = false;
        for (String id : TramaConfig.ADVANCEMENTS.get()) {
            var holder = p.server.getAdvancements().get(ResourceLocation.parse(id));
            if (holder != null && p.getAdvancements().getOrStartProgress(holder).isDone() && entry.advancements.add(id)) {
                granted += TramaConfig.ADVANCEMENT_XP.get(); changed = true;
            }
        }
        if (changed) {
            entry.experience += granted; ProgressData.get(p.server).setDirty(); sync(p);
            if (granted > 0) p.sendSystemMessage(Component.literal("Trama: conquistas anteriores reconhecidas, +"+granted+" XP de formação."));
        }
    }
    /** Reconcile lifecycle changes and apply the resulting build immediately. */
    public static void refresh(ServerPlayer p) {
        invalidate(p.getUUID()); sync(p); backfill(p);
        if (eligible(p)) {
            var s = state(p); TramaAttributes.apply(p,s.build.effects(context(p,s)));
        } else TramaAttributes.apply(p,null);
    }
    public static void interaction(ServerPlayer p, int kind) {
        if (!eligible(p)) return;
        state(p).activity.interact(kind);
    }
    private static void heal(ServerPlayer p, TramaState s) {
        boolean principle = s.build.has("SG9");
        if (!principle && !s.build.has("SG4") && !s.build.has("SG7")) return;
        long time = now(p);
        if (time-s.lastCombat < (principle ? 240 : 200) || time < s.nextHeal) return;
        s.nextHeal = time+(principle ? 80 : 100);
        float limit = p.getMaxHealth()*(principle ? .9f : s.build.has("SG7") ? .75f : .6f);
        if (p.getHealth() < limit) p.heal(Math.min(.5f,limit-p.getHealth()));
    }
    static void combat(ServerPlayer p, TramaState s) {
        s.lastCombat = now(p);
        entry(p).combatUntil = System.currentTimeMillis()+15_000;
        ProgressData.get(p.server).setDirty();
    }
    public static void reset(ServerPlayer p) {
        SkillsAPI.getCategory(CATEGORY).ifPresent(c -> c.erase(p));
        TramaAttributes.apply(p,null); forget(p.getUUID());
    }
    public static int respec(ServerPlayer p) {
        if (!eligible(p) || !sync(p)) { p.sendSystemMessage(Component.literal("Receba sua Casa para usar a Trama.")); return 0; }
        var entry = entry(p);
        long time = System.currentTimeMillis();
        if (time < entry.combatUntil) { p.sendSystemMessage(Component.literal("Espere 15 segundos fora de combate.")); return 0; }
        long wait = entry.lastRespec+TramaConfig.RESPEC_HOURS.get()*3_600_000L-time;
        if (entry.lastRespec != 0 && wait > 0) {
            p.sendSystemMessage(Component.literal("Novo respec em "+((wait+59_999)/60_000)+" minutos.")); return 0;
        }
        SkillsAPI.getCategory(CATEGORY).orElseThrow().resetSkills(p);
        entry.lastRespec = time; ProgressData.get(p.server).setDirty();
        forget(p.getUUID()); sync(p); TramaAttributes.apply(p,state(p).build.effects(context(p,state(p))));
        p.sendSystemMessage(Component.literal("Trama reorganizada. Seus pontos e sua formação foram preservados.")); return 1;
    }
    public static int open(ServerPlayer p) {
        if (!eligible(p) || !sync(p)) { p.sendSystemMessage(Component.literal("Receba sua Casa para abrir a Trama.")); return 0; }
        SkillsAPI.getCategory(CATEGORY).orElseThrow().openScreen(p); return 1;
    }
    public static int progress(ServerPlayer p) {
        if (!eligible(p) || !sync(p)) { p.sendSystemMessage(Component.literal("A Trama começa depois da criação e da vinculação à Casa.")); return 0; }
        var e = entry(p);
        var c = SkillsAPI.getCategory(CATEGORY).orElseThrow();
        p.sendSystemMessage(Component.literal("Trama: "+e.earned+"/45 pontos; "+c.getPointsLeft(p)+" disponíveis. Tempo ativo: "+e.activeSeconds/60+" min."));
        if (e.earned < 45) {
            long xp = ProgressRules.experienceRequired(e.earned+1,TramaConfig.XP_FIRST.get(),TramaConfig.XP_INCREMENT.get());
            long seconds = ProgressRules.secondsRequired(e.earned+1,TramaConfig.MINUTES_FIRST.get(),TramaConfig.MINUTES_INCREMENT.get());
            p.sendSystemMessage(Component.literal("Próximo ponto: faltam "+Math.max(0,xp-e.experience)+" XP de formação e "+Math.max(0,(seconds-e.activeSeconds+59)/60)+" min ativos."));
            p.sendSystemMessage(Component.literal("Descubra biomas, conclua conquistas e pratique ativamente. Mobs repetidos e AFK não rendem XP da Trama."));
        }
        return 1;
    }
}

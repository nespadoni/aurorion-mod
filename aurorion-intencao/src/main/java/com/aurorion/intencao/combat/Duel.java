package com.aurorion.intencao.combat;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.character.CharacterSpells;
import com.aurorion.core.character.SheetCheck;
import com.aurorion.intencao.config.IntencaoConfig;
import com.aurorion.intencao.network.DuelPayloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Um duelo com a ficha: turnos pela iniciativa, cada um escolhe a ação, e o golpe que acerta abre a reação
 * cronometrada de quem apanha (aparo ou esquiva, a janela cresce com a perícia). Tudo no servidor: o
 * cliente só mostra e manda o clique.
 *
 * <p>Custo: um duelo vive em memória e só faz algo quando o relógio dele vence (fim da contagem, prazo da
 * escolha, instante do golpe) ou quando chega um pacote; o resto dos ticks é uma comparação de número.
 */
final class Duel {
    enum Phase { COUNTDOWN, CHOOSING, REACTING, BETWEEN, ENDED }

    static final int MELEE = 0, RANGED = 1, DEFEND = 2, TECHNIQUE = 3, SPELL = 4, SURRENDER = 5;
    /** Do golpe anunciado até o impacto: o tempo de ver o anel fechar. */
    private static final int REACT_TICKS = 24;
    /** Depois do impacto, quanto se espera o clique chegar pela rede. */
    private static final int GRACE_TICKS = 10;
    private static final int BETWEEN_TICKS = 30;

    private static final class Pending {
        final int attacker, target;
        final String label;
        final int dice, sides, flat;
        final long impactTick;
        final int windowMs, perfectMs;
        /** O total do ataque (d20 + bônus): a CD da reação de quem apanha. */
        final int attackTotal;
        int reaction = -1;
        boolean crit;
        int offsetMs;

        Pending(int attacker, int target, String label, int dice, int sides, int flat, long impactTick, int windowMs, int perfectMs,
                int attackTotal) {
            this.attackTotal = attackTotal;
            this.sides = sides;
            this.attacker = attacker;
            this.target = target;
            this.label = label;
            this.dice = dice;
            this.flat = flat;
            this.impactTick = impactTick;
            this.windowMs = windowMs;
            this.perfectMs = perfectMs;
        }
    }

    final UUID id = UUID.randomUUID();
    final List<Combatant> fighters;
    final int limit;
    final ResourceKey<Level> dimension;
    final Vec3 center;
    /** 0: sem campo (a arena do Plano da Intenção já prende). */
    final int radius;
    @Nullable final UUID planeSession;
    /** Quem já recebeu a arena (duelistas e plateia): o fim vai para os mesmos. */
    final java.util.Set<UUID> viewers = new java.util.HashSet<>();
    boolean arenaClosed;
    private final RandomSource random = RandomSource.create();
    private final List<Integer> order = new ArrayList<>();
    private int turn = -1;
    private int round;
    Phase phase = Phase.COUNTDOWN;
    private long until;
    boolean paused;
    private long pausedLeft;
    private String log = "";
    @Nullable private Pending pending;
    /** O Defender da vez veio do prazo (já registrado como "hesitou"). */
    private boolean timedOut;

    Duel(List<Combatant> fighters, int limit, ResourceKey<Level> dimension, Vec3 center, int radius, @Nullable UUID planeSession) {
        this.fighters = List.copyOf(fighters);
        this.limit = limit;
        this.dimension = dimension;
        this.center = center;
        this.radius = radius;
        this.planeSession = planeSession;
    }

    int indexOf(UUID player) {
        for (int i = 0; i < fighters.size(); i++) if (fighters.get(i).id.equals(player)) return i;
        return -1;
    }

    // --- Ciclo -----------------------------------------------------------------------------------

    void start(MinecraftServer server, long now) {
        for (Combatant c : fighters) c.initiativeRoll = 1 + random.nextInt(20) + c.initiative;
        for (int i = 0; i < fighters.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> fighters.get(i).initiativeRoll).reversed());
        phase = Phase.COUNTDOWN;
        until = now + IntencaoConfig.COUNTDOWN_SECONDS.get() * 20L;
        log = "Duelo! Ordem: " + String.join(" → ", order.stream().map(i -> fighters.get(i).name).toList());
        broadcast(server, now);
    }

    void tick(MinecraftServer server, long now) {
        if (paused || phase == Phase.ENDED || now < until) return;
        switch (phase) {
            case COUNTDOWN, BETWEEN -> nextTurn(server, now);
            case CHOOSING -> {
                Combatant actor = fighters.get(order.get(turn));
                log = actor.name + " hesitou e se defendeu.";
                timedOut = true;
                act(server, order.get(turn), DEFEND, 0, -1, now);
            }
            case REACTING -> resolve(server, now);
            default -> {
            }
        }
    }

    void pause(MinecraftServer server, long now, boolean value) {
        if (paused == value || phase == Phase.ENDED) return;
        paused = value;
        if (value) pausedLeft = Math.max(0, until - now);
        else until = now + pausedLeft;
        log = value ? "O mestre pausou o duelo." : "O duelo continua.";
        broadcast(server, now);
    }

    private void nextTurn(MinecraftServer server, long now) {
        if (checkEnd(server)) return;
        for (int guard = 0; guard < order.size() * 2; guard++) {
            turn++;
            if (turn >= order.size()) {
                turn = 0;
                round++;
                if (round >= IntencaoConfig.MAX_ROUNDS.get()) {
                    // Ninguém fica em duelo (e longe do resto do mundo) para sempre: empate.
                    log = "O tempo do duelo acabou.";
                    phase = Phase.ENDED;
                    pending = null;
                    broadcast(server, now);
                    DuelManager.finish(server, this, -1);
                    return;
                }
            }
            Combatant actor = fighters.get(order.get(turn));
            if (actor.defeated(limit) || server.getPlayerList().getPlayer(actor.id) == null) continue;
            actor.defending = false;
            actor.stumbling = false;
            if (actor.exhausted) {
                actor.exhausted = false;
                log = actor.name + " está exausto e perde a vez.";
                continue;
            }
            actor.pe = Math.min(actor.peMax, actor.pe + 1);
            phase = Phase.CHOOSING;
            until = now + IntencaoConfig.TURN_SECONDS.get() * 20L;
            broadcast(server, now);
            prompt(server, order.get(turn));
            return;
        }
        // Ninguém pode agir (todos fora): encerra pelo que sobrou.
        checkEnd(server);
    }

    // --- Entrada -----------------------------------------------------------------------------------

    void input(MinecraftServer server, ServerPlayer player, DuelPayloads.Input input, long now) {
        int index = indexOf(player.getUUID());
        if (index < 0 || paused) return;
        if (input.kind() == 0) {
            if (phase != Phase.CHOOSING || order.get(turn) != index) return;
            if (input.a() == SURRENDER) {
                surrender(server, index, now);
                return;
            }
            act(server, index, input.a(), input.b(), input.c(), now);
        } else if (input.kind() == 1) {
            Pending p = pending;
            if (phase != Phase.REACTING || p == null || p.target != index || p.reaction >= 0) return;
            if (now > p.impactTick + GRACE_TICKS) return;
            p.reaction = input.a() == 1 ? 1 : 0;
            p.offsetMs = plausibleOffset(input.b(), (now - p.impactTick) * 50L);
        }
    }

    void surrender(MinecraftServer server, int index, long now) {
        Combatant c = fighters.get(index);
        if (c.out || phase == Phase.ENDED) return;
        c.out = true;
        c.surrendered = true;
        log = c.name + " desistiu.";
        if (!checkEnd(server)) {
            if (phase == Phase.CHOOSING && order.get(turn) == index) {
                phase = Phase.BETWEEN;
                until = now + BETWEEN_TICKS;
            }
            broadcast(server, now);
        }
    }

    /** Quem saiu do jogo: derrotado, e o turno dele (se era) passa. */
    void left(MinecraftServer server, UUID player, long now) {
        int index = indexOf(player);
        if (index < 0 || phase == Phase.ENDED) return;
        Combatant c = fighters.get(index);
        c.out = true;
        log = c.name + " fugiu do duelo.";
        boolean involved = pending != null && (pending.attacker == index || pending.target == index);
        if (involved) pending = null;
        boolean acting = phase == Phase.CHOOSING && turn >= 0 && turn < order.size() && order.get(turn) == index;
        if (!checkEnd(server)) {
            // Num combate de vários, a saída de um terceiro não tira a vez de quem está agindo.
            if (involved || acting) {
                phase = Phase.BETWEEN;
                until = now + BETWEEN_TICKS;
            }
            broadcast(server, now);
        }
    }

    // --- Ações -------------------------------------------------------------------------------------

    private void act(MinecraftServer server, int index, int kind, int option, int targetIndex, long now) {
        Combatant actor = fighters.get(index);
        Combatant target = targetIndex >= 0 && targetIndex < fighters.size() ? fighters.get(targetIndex) : null;
        boolean validTarget = target != null && target.side != actor.side && !target.defeated(limit);
        if (!validTarget) target = firstEnemy(actor);

        switch (kind) {
            case DEFEND -> {
                actor.defending = true;
                // Gastar o turno se defendendo descansa a guarda.
                actor.guardStreak = 0;
                actor.pe = Math.min(actor.peMax, actor.pe + 1);
                if (!timedOut) log = actor.name + " se põe em guarda.";
                timedOut = false;
                between(server, now);
            }
            case MELEE, RANGED -> {
                if (target == null) {
                    between(server, now);
                    return;
                }
                boolean melee = kind == MELEE;
                // Ataque básico: 3d12 + MO (o modificador de ataque), × E da alma (RPG v0.3 §09).
                int bonus = melee ? actor.melee : actor.ranged;
                attack(server, index, indexOf(target.id), bonus, melee ? "golpe" : "disparo", Combatant.BASIC_DICE, 12, bonus, now);
            }
            case TECHNIQUE -> {
                if (target == null || option < 0 || option >= actor.techniques.size()) return;
                CharacterSheet.Technique technique = actor.techniques.get(option);
                int cost = technique.tier();
                if (actor.pe < cost) return;
                actor.pe -= cost;
                int bonus = Math.max(actor.melee, actor.ranged);
                // Técnica: o pacote do nível da habilidade, H1 4d12 … H5 12d12 (RPG v0.3 §09).
                int level = Math.max(1, Math.min(5, technique.level()));
                attack(server, index, indexOf(target.id), bonus, technique.name(), Combatant.TECHNIQUE_DICE[level - 1], 12, bonus, now);
            }
            case SPELL -> {
                if (target == null || option < 0 || option >= actor.spells.size()) return;
                CharacterSpells.Option spell = actor.spells.get(option);
                int cost = spell.rarity() + 1;
                if (actor.pe < cost) return;
                actor.pe -= cost;
                spell(server, index, indexOf(target.id), spell, now);
            }
            default -> {
            }
        }
    }

    private void attack(MinecraftServer server, int attacker, int target, int bonus, String label, int dice, int sides, int flat,
                        long now) {
        Combatant a = fighters.get(attacker), t = fighters.get(target);
        int die = 1 + random.nextInt(SheetCheck.DIE);
        SheetCheck.Outcome roll = attackRoll(die, bonus, t.effectiveDefense());
        int total = die + bonus;
        switch (roll) {
            case CRIT_FAIL -> {
                a.stumbling = true;
                log = a.name + " erra feio (" + label + ") e fica exposto.";
                between(server, now);
            }
            case FAIL -> {
                log = a.name + " erra (" + label + ").";
                between(server, now);
            }
            // Crítico rola o dobro dos dados; o MO entra uma vez e só então vem E (RPG v0.3 §10).
            default -> strike(server, attacker, target, label, roll == SheetCheck.Outcome.CRIT ? dice * 2 : dice, sides, flat,
                    roll == SheetCheck.Outcome.CRIT, total, now);
        }
    }

    private void spell(MinecraftServer server, int caster, int target, CharacterSpells.Option spell, long now) {
        Combatant a = fighters.get(caster);
        String name = spell.id().contains(":") ? spell.id().substring(spell.id().indexOf(':') + 1) : spell.id();
        int die = 1 + random.nextInt(SheetCheck.DIE);
        if (spell.advantage()) die = Math.max(die, 1 + random.nextInt(SheetCheck.DIE));
        SheetCheck.Outcome roll = attackRoll(die, spell.bonus(), spell.dc());
        int total = die + spell.bonus();
        switch (roll) {
            case CRIT_FAIL -> {
                a.exhausted = true;
                log = "O fio de " + a.name + " estalou (" + name + "): exausto, perde o próximo turno.";
                between(server, now);
            }
            case FAIL -> {
                log = "A magia de " + a.name + " se desfez (" + name + ").";
                between(server, now);
            }
            default -> {
                // Magia: o pacote pela raridade, comum = H1 … lendária = H5 (RPG v0.3 §09).
                int level = Math.max(1, Math.min(5, spell.rarity() + 1));
                boolean crit = roll == SheetCheck.Outcome.CRIT;
                int dice = Combatant.TECHNIQUE_DICE[level - 1] * (crit ? 2 : 1);
                strike(server, caster, target, name, dice, 12, spell.bonus(), crit, total, now);
            }
        }
    }

    /** O golpe acertou: abre a reação de quem apanha e espera o instante do impacto. */
    private void strike(MinecraftServer server, int attacker, int target, String label, int dice, int sides, int flat, boolean crit,
                        int attackTotal, long now) {
        Combatant a = fighters.get(attacker), t = fighters.get(target);
        int base = IntencaoConfig.REACTION_MS.get(), per = IntencaoConfig.REACTION_MS_PER_POINT.get(), max = IntencaoConfig.REACTION_MS_MAX.get();
        int skill = Math.max(t.dodge, t.parry);
        int window = Math.min(max, base + per * Math.max(0, skill));
        int perfect = Math.max(30, window / 3);
        pending = new Pending(attacker, target, label, dice, sides, flat, now + REACT_TICKS, window, perfect, attackTotal);
        if (crit) pending.crit = true;
        phase = Phase.REACTING;
        until = now + REACT_TICKS + GRACE_TICKS;
        log = a.name + (crit ? " acerta em cheio: " : " ataca: ") + label + " → " + t.name + " (ataque " + attackTotal + ")";
        broadcast(server, now);
        ServerPlayer victim = server.getPlayerList().getPlayer(t.id);
        if (victim != null) {
            PacketDistributor.sendToPlayer(victim, new DuelPayloads.React(a.name, label + " · CD " + attackTotal, REACT_TICKS, window, perfect));
        }
    }

    private void resolve(MinecraftServer server, long now) {
        Pending p = pending;
        pending = null;
        if (p == null) {
            between(server, now);
            return;
        }
        Combatant a = fighters.get(p.attacker), t = fighters.get(p.target);
        // Ordem do RPG v0.3 §10: dados e crítico → MO uma vez → E e arredondamento → metade por defesa → RD →
        // mitigação reativa (aparo) → Vida.
        int sum = 0;
        for (int i = 0; i < p.dice; i++) sum += 1 + random.nextInt(p.sides);
        int damage = Combatant.damage(sum, p.flat, a.scale);
        if (t.defending) damage /= 2;
        damage = Math.max(0, damage - t.rd);
        int abs = Math.abs(p.offsetMs);
        String how;
        boolean counter = false;
        if (p.reaction >= 0 && abs <= p.windowMs) {
            // O tempo só dá bônus: a reação é um teste contra o total do ataque.
            Reaction r = reaction(p.reaction == 0, t.parry, t.dodge, abs <= p.perfectMs, t.guardStreak, p.attackTotal,
                    1 + random.nextInt(SheetCheck.DIE), IntencaoConfig.PERFECT_BONUS.get(), IntencaoConfig.TIMING_BONUS.get(),
                    IntencaoConfig.GUARD_FATIGUE.get());
            damage = r.damage(damage);
            counter = r.counter();
            t.guardStreak = r.success() ? t.guardStreak + 1 : 0;
            if (r.success()) t.defended++;
            how = t.name + " " + r.describe();
        } else {
            t.guardStreak = 0;
            how = p.reaction < 0 ? t.name + " não reagiu." : t.name + " reagiu fora do tempo.";
        }
        t.hp = Math.max(0, t.hp - damage);
        a.dealt += damage;
        t.taken += damage;
        if (damage > 0) {
            a.hits++;
            if (p.crit) {
                a.crits++;
                DuelManager.lightning(server, this, t.id);
            }
        }
        log = how + (damage > 0 ? " −" + damage + " de vida em " + t.name + "." : "");
        effects(server, t, damage, counter);
        if (counter && !a.defeated(limit)) {
            // Contra-ataque: 2d12 + Força × E de quem aparou, menos a RD (uma resposta, abaixo do básico).
            int roll = 2 + random.nextInt(12) + random.nextInt(12);
            int back = Math.max(1, Combatant.damage(roll, t.strength, t.scale) - a.rd);
            a.hp = Math.max(0, a.hp - back);
            t.dealt += back;
            a.taken += back;
            log += " Contra-ataque: −" + back + " em " + a.name + ".";
            effects(server, a, back, false);
        }
        if (!checkEnd(server)) between(server, now);
    }

    /**
     * O resultado de uma reação no tempo: d20 + Corpo a corpo (aparo) ou Acrobacia (esquiva) + o bônus do
     * tempo − a guarda cansada, contra o total do ataque. 1 natural sempre falha, 20 sempre passa.
     * <ul>
     *   <li>aparo: passou = metade do dano; passou por 10 ou mais no instante perfeito = nada e contra-ataque;
     *       falhou = o golpe inteiro;</li>
     *   <li>esquiva: passou = nada; falhou = o golpe inteiro (arrisca mais que o aparo).</li>
     * </ul>
     */
    record Reaction(boolean parry, int die, int total, int dc, boolean success, boolean counter, int fatigue, int timing) {
        int damage(int full) {
            if (!success) return full;
            return parry && !counter ? full / 2 : 0;
        }

        String describe() {
            String math = "(d20 " + die + " → " + total + " contra " + dc
                    + (timing > 0 ? ", tempo +" + timing : "") + (fatigue > 0 ? ", guarda cansada −" + fatigue : "") + ")";
            if (!success) return (parry ? "tentou aparar e não segurou " : "tentou esquivar e foi pego ") + math + ".";
            if (counter) return "fez um APARO PERFEITO " + math + "!";
            return (parry ? "aparou parte " : "esquivou ") + math + ".";
        }
    }

    /**
     * Ataque no RPG v0.3 (§07, §10): precisa igualar a Defesa; 20 natural acerta e é crítico; 1 natural falha
     * (e deixa exposto). Passar por 10 não é crítico em ataque — a margem expandida (18–20) é de técnica.
     * A regra genérica de teste ({@code SheetCheck.judge}) dava 15–20% de crítico nas simulações; esta, 5%.
     */
    static SheetCheck.Outcome attackRoll(int die, int bonus, int defense) {
        if (die >= SheetCheck.DIE) return SheetCheck.Outcome.CRIT;
        if (die <= 1) return SheetCheck.Outcome.CRIT_FAIL;
        return die + bonus >= defense ? SheetCheck.Outcome.SUCCESS : SheetCheck.Outcome.FAIL;
    }

    /**
     * O cliente diz a que distância do impacto clicou; o servidor só aceita o que bate com a hora em que o
     * pacote chegou (chegada = impacto + desvio + latência). Um cliente que sempre diz "0" fica fora da
     * janela quando o pacote chega tarde ou cedo demais. {@code serverMs}: chegada − impacto, em ms (precisão
     * de um tick).
     */
    static int plausibleOffset(int claimedMs, long serverMs) {
        long claimed = Math.max(-5000, Math.min(5000, claimedMs));
        // Latência de 0 a 600 ms e um tick de folga para cada lado.
        if (claimed > serverMs + 60 || claimed < serverMs - 660) return 100_000;
        return (int) claimed;
    }

    static Reaction reaction(boolean parry, int parrySkill, int dodgeSkill, boolean perfect, int streak, int dc, int die,
                             int perfectBonus, int timingBonus, int fatiguePerDefense) {
        int timing = perfect ? perfectBonus : timingBonus;
        int fatigue = streak * fatiguePerDefense;
        int total = die + (parry ? parrySkill : dodgeSkill) + timing - fatigue;
        boolean success = die == SheetCheck.DIE || die != 1 && total >= dc;
        boolean counter = parry && success && perfect && total - dc >= SheetCheck.CRIT_MARGIN;
        return new Reaction(parry, die, total, dc, success, counter, fatigue, timing);
    }

    private void between(MinecraftServer server, long now) {
        if (phase == Phase.ENDED) return;
        if (checkEnd(server)) return;
        phase = Phase.BETWEEN;
        until = now + BETWEEN_TICKS;
        broadcast(server, now);
    }

    @Nullable
    private Combatant firstEnemy(Combatant actor) {
        for (Combatant c : fighters) if (c.side != actor.side && !c.defeated(limit)) return c;
        return null;
    }

    private void effects(MinecraftServer server, Combatant hit, int damage, boolean parried) {
        ServerPlayer player = server.getPlayerList().getPlayer(hit.id);
        if (player == null) return;
        var level = player.serverLevel();
        if (parried) {
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.2, player.getZ(), 14, .3, .3, .3, .2);
            level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.3f);
        } else if (damage > 0) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.2, player.getZ(), Math.min(10, 2 + damage), .3, .3, .3, .1);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 1f);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1f, 1.4f);
        }
    }

    // --- Fim ---------------------------------------------------------------------------------------

    /** Algum lado inteiro foi derrotado? Encerra e devolve {@code true}. */
    private boolean checkEnd(MinecraftServer server) {
        if (phase == Phase.ENDED) return true;
        List<Integer> standing = new ArrayList<>();
        for (Combatant c : fighters) if (!c.defeated(limit) && !standing.contains(c.side)) standing.add(c.side);
        if (standing.size() > 1) return false;
        phase = Phase.ENDED;
        pending = null;
        broadcast(server, 0);
        DuelManager.finish(server, this, standing.isEmpty() ? -1 : standing.get(0));
        return true;
    }

    // --- Rede --------------------------------------------------------------------------------------

    void broadcast(MinecraftServer server, long now) {
        List<DuelPayloads.Fighter> list = new ArrayList<>(fighters.size());
        for (Combatant c : fighters) {
            int flags = (c.defending ? 1 : 0) | (c.defeated(limit) ? 2 : 0) | (c.out ? 4 : 0) | (c.exhausted ? 8 : 0);
            list.add(new DuelPayloads.Fighter(c.name, c.side, c.color, c.hp, c.hpMax, c.pe, c.peMax, limit == 0 ? 0 : c.stopHp(limit), flags));
        }
        int actor = turn >= 0 && turn < order.size() ? order.get(turn) : -1;
        int seconds = phase == Phase.COUNTDOWN || phase == Phase.CHOOSING ? (int) Math.max(0, (until - now + 19) / 20) : 0;
        if (paused) seconds = (int) (pausedLeft / 20);
        for (int i = 0; i < fighters.size(); i++) {
            ServerPlayer player = server.getPlayerList().getPlayer(fighters.get(i).id);
            if (player == null) continue;
            PacketDistributor.sendToPlayer(player, new DuelPayloads.State(list, i, actor, round, limit, phase.ordinal(),
                    seconds, log, paused));
        }
    }

    private void prompt(MinecraftServer server, int index) {
        Combatant actor = fighters.get(index);
        ServerPlayer player = server.getPlayerList().getPlayer(actor.id);
        if (player == null) return;
        List<DuelPayloads.Choice> choices = new ArrayList<>();
        choices.add(new DuelPayloads.Choice(MELEE, 0, "Atacar corpo a corpo (+" + actor.melee + ")", 0, true));
        choices.add(new DuelPayloads.Choice(RANGED, 0, "Atacar à distância (+" + actor.ranged + ")", 0, true));
        choices.add(new DuelPayloads.Choice(DEFEND, 0, "Defender (+3 defesa, metade do dano, +1 PE)", 0, true));
        for (int i = 0; i < actor.techniques.size() && choices.size() < 16; i++) {
            CharacterSheet.Technique t = actor.techniques.get(i);
            choices.add(new DuelPayloads.Choice(TECHNIQUE, i, t.name() + " " + "★".repeat(Math.max(1, Math.min(5, t.tier()))), t.tier(), actor.pe >= t.tier()));
        }
        for (int i = 0; i < actor.spells.size() && choices.size() < 23; i++) {
            CharacterSpells.Option s = actor.spells.get(i);
            choices.add(new DuelPayloads.Choice(SPELL, i, s.name(), s.rarity() + 1, actor.pe >= s.rarity() + 1));
        }
        choices.add(new DuelPayloads.Choice(SURRENDER, 0, "Desistir", 0, true));
        List<DuelPayloads.Target> targets = new ArrayList<>();
        for (int i = 0; i < fighters.size(); i++) {
            Combatant c = fighters.get(i);
            if (c.side != actor.side && !c.defeated(limit)) targets.add(new DuelPayloads.Target(i, c.name));
        }
        PacketDistributor.sendToPlayer(player, new DuelPayloads.Prompt(IntencaoConfig.TURN_SECONDS.get(), choices, targets));
    }
}

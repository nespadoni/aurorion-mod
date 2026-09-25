package com.aurorion.magia.passive;

import com.aurorion.magia.compat.EmotecraftCompat;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.AurorionSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A <b>Presenca Aterradora</b>: o que acontece em volta de quem carrega a passiva do medo.
 *
 * <h2>O que ela faz</h2>
 *
 * <ul>
 *   <li><b>Medo</b>, ate {@code dreadRadius} blocos (30 por padrao): quem estiver dentro recebe
 *       {@code apavorado} e, com {@code dreadDarkens} ligado, a <b>Escuridao</b> do vanilla. O nosso
 *       efeito nao mexe em atributo nenhum — ele e lido pelo cliente da propria pessoa e vira tela
 *       preta fechando, tremor, batida de coracao e nevoa negra; a Escuridao e o que apaga a luz do
 *       mundo de verdade, inclusive para quem joga com shader pack. Nao tira vida, nao tira
 *       velocidade e nao vira arma de PvP disfarçada.</li>
 *   <li><b>Prostracao</b>, ate {@code dreadKneelRadius} blocos (o raio inteiro, por padrao): quem
 *       sente o medo <b>se prostra</b> — os dois joelhos no chao, pose propria do Emotecraft
 *       ({@code kneel_down}), ou um joelho so com {@code dreadProstrates = false}. As maos continuam
 *       livres — comer, erguer escudo e conjurar seguem funcionando, ao contrario da magia
 *       Prostracao. Uma aura que fica ligada por tempo indeterminado e vale para qualquer um que passe
 *       nao pode tirar o item da mao das pessoas: atravessar a rua onde o vilao esta viraria
 *       impossibilidade de jogar em vez de susto.</li>
 *   <li><b>Criaturas fogem</b>: mob no raio recebe {@code apavorado} (a marca que o
 *       {@code LivingChangeTargetEvent} le para nao deixar bicho apavorado voltar a mirar quem o
 *       apavorou), perde o alvo e corre para longe.</li>
 * </ul>
 *
 * <p>Ficam de fora: espectador, staff em criativo, entidade invulneravel (NPC de oficio, manequim) e,
 * se {@code dreadSparesAllies} estiver ligado, os aliados de time do portador — os capangas do vilao
 * nao se prostram para ele.
 *
 * <h2>Custo</h2>
 *
 * <p>O relogio e o tick do efeito {@code presenca_terrivel} no proprio portador
 * ({@code DreadAuraEffect}), e nao um listener de tick de servidor: com nenhuma aura ligada, o custo e
 * zero. Por aura ligada:
 *
 * <ul>
 *   <li><b>Gente: nenhuma busca espacial.</b> As pessoas saem de {@code level.players()}, lista que o
 *       servidor ja mantem — uma volta nela e uma distancia ao quadrado por jogador, uma vez por
 *       segundo. Aumentar {@code dreadRadius} passa a nao custar nada, e manequim, moldura e item no
 *       chao nunca entram na conta (a busca antiga em {@code LivingEntity} colhia todos eles para
 *       depois jogar fora, e ainda punha efeito em armadura de manequim).</li>
 *   <li><b>Bicho: uma busca em esfera a cada dois segundos</b>, so em {@code Mob}, e no maximo
 *       {@value #MAX_PATHS} rotas de fuga calculadas por varredura — pathfinding e o que custa caro, e
 *       quem ja esta fugindo nao recebe rota nova. Perder o alvo, que e o que importa, vale para todos
 *       os encontrados.</li>
 * </ul>
 *
 * <p>Os efeitos nos atingidos duram pouco mais que a varredura que os renova — quem sai do raio volta
 * ao normal sozinho, sem ninguem varrer lista. Uma unica mensagem de visual sai por pulso, para quem
 * ja rastreia o portador; a nevoa, os vultos e o coracao sao desenhados e tocados por cada cliente.
 */
public final class DreadAura {
    /** O pulso da aura: uma vez por segundo. */
    public static final int INTERVAL_TICKS = 20;
    /** Duracao dos efeitos em gente: um pulso e meio, para dar sobreposicao entre as varreduras. */
    private static final int VICTIM_TICKS = INTERVAL_TICKS + 15;
    /**
     * Duracao da Escuridao, mais longa que a do medo de proposito. O vanilla acende e apaga esse efeito
     * em rampa nos ultimos 22 ticks dele; se a renovacao caisse dentro dessa janela, a luz do mundo
     * piscaria uma vez por segundo em vez de ficar apagada. Com {@value #DARK_TICKS} ticks sobram 30 no
     * momento em que o pulso seguinte renova — nunca dentro da rampa. O preco e a escuridao demorar
     * cerca de dois segundos e meio para levantar depois que a pessoa sai do raio, e isso assenta bem.
     */
    private static final int DARK_TICKS = INTERVAL_TICKS * 2 + 10;
    /** A varredura de bicho e mais lenta: fuga nao precisa de resposta em um segundo. */
    private static final int MOB_INTERVAL_TICKS = INTERVAL_TICKS * 2;
    private static final int MOB_TICKS = MOB_INTERVAL_TICKS + 15;
    /**
     * Prazo do visual no cliente. Tres pulsos: um pacote perdido, ou o portador saindo e voltando ao
     * alcance de rastreio, nao pisca a nevoa de quem esta com medo.
     */
    private static final int VISUAL_TICKS = INTERVAL_TICKS * 3;
    /** Teto de gente por pulso. Uma praça cheia nao vira 80 efeitos por segundo. */
    private static final int MAX_AFFECTED = 32;
    /** Teto de bichos por varredura: uma granja de mobs ao lado do vilao nao vira 200 efeitos. */
    private static final int MAX_CREATURES = 48;
    /** Teto de rotas de fuga por varredura: e o pathfinding que pesa, nao o efeito. */
    private static final int MAX_PATHS = 8;

    private DreadAura() {
    }

    /** Liga a aura: o marcador infinito no portador e quem faz o resto acontecer. */
    public static void enable(ServerPlayer owner) {
        owner.addEffect(new MobEffectInstance(MagiaEffects.DREAD_AURA, MobEffectInstance.INFINITE_DURATION, 0,
                false, false, false));
        pulse(owner, true);
    }

    public static void disable(ServerPlayer owner) {
        if (owner.hasEffect(MagiaEffects.DREAD_AURA)) owner.removeEffect(MagiaEffects.DREAD_AURA);
    }

    /**
     * Entrou no servidor com a passiva ligada: o marcador volta. O efeito nao e salvo de proposito —
     * quem manda e o cadastro da passiva, e nao um efeito que poderia ficar para tras num crash.
     */
    public static void restore(ServerPlayer owner) {
        if (PassiveData.get(owner.server).isActive(owner.getUUID(), Passive.DREAD)) {
            enable(owner);
        } else {
            disable(owner);
        }
    }

    /** Um pulso, chamado pelo tick do efeito do portador. */
    public static void pulse(ServerPlayer owner) {
        // Metade dos pulsos cuida de bicho; o relogio e o tick do proprio portador, sem contador nosso.
        pulse(owner, owner.tickCount % MOB_INTERVAL_TICKS < INTERVAL_TICKS);
    }

    private static void pulse(ServerPlayer owner, boolean creatures) {
        if (!(owner.level() instanceof ServerLevel level) || owner.isSpectator() || !owner.isAlive()) return;
        // A passiva pode ter sido revogada pela staff enquanto a aura estava ligada.
        if (!PassiveData.get(owner.server).isActive(owner.getUUID(), Passive.DREAD)) {
            disable(owner);
            return;
        }

        double radius = MagiaConfig.DREAD_RADIUS.get();
        double radiusSqr = radius * radius;
        // Raio de joelho maior que o do medo nao existe: quem nao sente a aura nao se prostra por ela.
        double kneelRadius = Math.min(MagiaConfig.DREAD_KNEEL_RADIUS.get(), radius);
        double kneelSqr = kneelRadius * kneelRadius;
        boolean spareAllies = MagiaConfig.DREAD_SPARE_ALLIES.get();
        boolean darkens = MagiaConfig.DREAD_DARKENS.get();
        boolean deep = MagiaConfig.DREAD_PROSTRATES.get();
        Vec3 center = owner.position();

        MagiaNetwork.sendVisual(owner, owner, SpellVisualPayload.Kind.TERROR_AURA,
                VISUAL_TICKS, center, (float) radius);

        int touched = 0;
        for (ServerPlayer victim : level.players()) {
            if (touched >= MAX_AFFECTED) break;
            if (victim == owner || !reaches(owner, victim, spareAllies)) continue;
            double distanceSqr = victim.position().distanceToSqr(center);
            if (distanceSqr > radiusSqr) continue;
            touched++;
            terrify(victim, owner, darkens);
            if (kneelRadius > 0 && distanceSqr <= kneelSqr) prostrate(victim, owner, deep);
        }

        if (creatures) scatter(level, owner, center, radius, spareAllies);
    }

    /** Quem a aura alcança entre as pessoas da dimensao. */
    private static boolean reaches(ServerPlayer owner, ServerPlayer victim, boolean spareAllies) {
        return victim.isAlive() && !victim.isSpectator() && !victim.isCreative()
                && !(spareAllies && victim.isAlliedTo(owner));
    }

    /**
     * O medo. {@code apavorado} e lido pelo cliente da propria pessoa (tela, som, nevoa); a Escuridao
     * do vanilla e o que apaga a luz do mundo em volta, e ela atravessa shader pack — o pacote de
     * shaders respeita a iluminacao do jogo, e nao a neblina que a gente pede.
     */
    private static void terrify(ServerPlayer victim, ServerPlayer owner, boolean darkens) {
        victim.addEffect(new MobEffectInstance(MagiaEffects.TERRIFIED, VICTIM_TICKS, 0, false, false, true), owner);
        if (darkens) {
            // Sem icone: o de apavorado ja esta na tela, e dois icones para o mesmo susto e ruido.
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_TICKS, 0, false, false, false), owner);
        }
    }

    /**
     * De joelhos. Renovado a cada pulso; sair do raio deixa o efeito expirar sozinho.
     *
     * @param deep os dois joelhos no chao ({@code dreadProstrates}); {@code false} cede um joelho so
     */
    private static void prostrate(ServerPlayer victim, ServerPlayer owner, boolean deep) {
        boolean first = !victim.hasEffect(MagiaEffects.GENUFLECTED);
        victim.addEffect(new MobEffectInstance(MagiaEffects.GENUFLECTED, VICTIM_TICKS, 0, false, false, true), owner);
        if (!first) return;
        victim.setSprinting(false);
        EmotecraftCompat.prostrate(victim, deep);
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.5f, 0.5f);
    }

    /**
     * As criaturas. Todas as encontradas perdem o alvo e ficam marcadas de {@code apavorado} — e essa
     * marca que o {@code LivingChangeTargetEvent} le para nao deixar o bicho voltar a mirar quem o
     * apavorou —, ate {@value #MAX_CREATURES} por varredura. So as {@value #MAX_PATHS} primeiras ganham
     * rota de fuga: calcular caminho e o que custa caro, e quem ja esta correndo nao precisa de rota
     * nova.
     */
    private static void scatter(ServerLevel level, ServerPlayer owner, Vec3 center, double radius,
                                boolean spareAllies) {
        double radiusSqr = radius * radius;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius),
                mob -> mob.isAlive() && !AurorionSpell.untouchable(mob)
                        && !(spareAllies && mob.isAlliedTo(owner))
                        && mob.position().distanceToSqr(center) <= radiusSqr);

        int paths = 0;
        int touched = 0;
        for (Mob mob : mobs) {
            if (touched++ >= MAX_CREATURES) break;
            mob.addEffect(new MobEffectInstance(MagiaEffects.TERRIFIED, MOB_TICKS, 0, false, false, false), owner);
            if (mob.getTarget() != null) mob.setTarget(null);
            if (paths >= MAX_PATHS || !(mob instanceof PathfinderMob walker) || !walker.getNavigation().isDone()) {
                continue;
            }
            Vec3 away = DefaultRandomPos.getPosAway(walker, 16, 7, center);
            if (away == null) continue;
            walker.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
            paths++;
        }
    }
}

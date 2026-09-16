package com.aurorion.limbo.oracle;

import com.aurorion.limbo.config.LimboConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * O Oraculo, agora com corpo proprio: humanoide, com a skin do servidor, para ser reconhecivel de
 * longe quando muda de lugar todo dia.
 *
 * <p><b>Por que isto passou a ser uma entidade registrada.</b> O desenho antigo era deliberado — o
 * Oraculo era <i>qualquer mob com a tag</i>, e o acoplamento vivia no dado, nunca numa classe. Uma
 * skin humanoide propria nao cabe nesse arranjo: ela exige modelo, renderizador e textura, e nada
 * disso da para pendurar numa tag. A troca e consciente, e o contrato antigo <b>continua valendo</b>:
 * {@link #isOracle} aceita tanto esta entidade quanto qualquer mob que a staff tenha marcado, entao
 * um esqueleto com a tag continua atendendo exatamente como antes.
 *
 * <p>Ele nao anda, nao empurra, nao apanha e nao desaparece. O unico jeito de ele sair do lugar e a
 * rotacao diaria de {@link OracleRotation} ou um comando da staff.
 */
public class OracleEntity extends PathfinderMob {
    public OracleEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setSilent(true);
        setNoAi(true);
    }

    /**
     * O contrato de "isto e um Oraculo", num lugar so.
     *
     * <p>Aceita a tag para nao quebrar mundos onde a staff ja marcou um mob, e o tipo para o caso
     * novo. Ler a config aqui e seguro porque so o servidor pergunta.
     */
    public static boolean isOracle(Entity entity) {
        return entity instanceof OracleEntity || entity.getTags().contains(LimboConfig.ORACLE_TAG.get());
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override protected void registerGoals() {
        // Nenhum. Ele fica onde a rotacao o colocou.
    }
    // A imunidade vem de setInvulnerable(true), e nao de um hurt() que recusa tudo: o vanilla ja abre
    // excecao para /kill e para o vazio (DamageTypeTags.BYPASSES_INVULNERABILITY). Recusar todo dano
    // na mao deixaria a staff sem nenhum jeito de remover o NPC depois de invoca-lo.
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) { }
    @Override protected void pushEntities() { }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
    /** Sem gravidade nem deriva: o ponto cadastrado e exatamente onde ele fica. */
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean canBeLeashed() { return false; }
}

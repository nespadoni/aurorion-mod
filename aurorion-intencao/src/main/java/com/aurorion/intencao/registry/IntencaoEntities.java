package com.aurorion.intencao.registry;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.entity.EchoEntity;
import com.aurorion.intencao.entity.FragmentEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * As duas entidades do mod. Nenhuma tem IA nem vida; as duas conferem a cada poucos segundos se a
 * sessao delas ainda existe e somem sozinhas se nao — assim um chunk descarregado na hora do
 * veredito nao deixa sobra no mundo.
 *
 * <p>{@code noSummon}: nascem da batalha, nunca de {@code /summon}. Ao contrario das entidades do
 * magia, <b>sao salvas</b>: uma batalha atravessa um restart do servidor, e o Eco precisa continuar
 * la quando o mundo voltar.
 */
public final class IntencaoEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AurorionIntencao.MOD_ID);

    /** O corpo em transe que fica no lugar de quem foi chamado ({@link EchoEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<EchoEntity>> ECHO =
            ENTITIES.register("eco", () -> EntityType.Builder
                    .<EchoEntity>of(EchoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.2F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("eco"));

    /** Fragmento de memoria flutuando no plano ({@link FragmentEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<FragmentEntity>> FRAGMENT =
            ENTITIES.register("fragmento", () -> EntityType.Builder
                    .<FragmentEntity>of(FragmentEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(4)
                    .updateInterval(40)
                    .build("fragmento"));

    private IntencaoEntities() {
    }
}

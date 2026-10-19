package com.aurorion.mundos.perfil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * Como um mundo se comporta: quao fortes nascem os monstros, quantos cabem nele e quantas estruturas
 * o terreno tem. Mora em {@code data/<namespace>/aurorion/perfis_mundo/<nome>.json}.
 *
 * <p>Um perfil e um <b>molde</b>, e nao um mundo: o mesmo "pesadelo" serve ao mundo de exploracao de
 * hoje e ao servidor de evento de amanha. Quem liga um perfil a uma dimensao e, pela ordem: a config
 * ({@code /mundos dificuldade mundo}), o campo {@code dimensoes} do proprio perfil, e por ultimo o
 * perfil global ({@code /mundos dificuldade global}).
 *
 * <pre>{@code
 * {
 *   "descricao": "Monstros cinco vezes mais vivos, estruturas por toda parte",
 *   "dimensoes": ["aurorion_mundos:mundo_tres"],
 *   "monstros": { "vida": 5.0, "dano": 2.0, "armadura": 4, "experiencia": 3.0 },
 *   "spawn": { "tetoMonstros": 1.5 },
 *   "estruturas": { "densidade": 2.0, "conjuntos": { "minecraft:villages": 0.5 } }
 * }
 * }</pre>
 *
 * <p>Todo campo e opcional, e o valor ausente e o do vanilla: um perfil so com {@code "monstros":
 * {"vida": 3}} so mexe na vida.
 */
public record WorldProfile(
        ResourceLocation id,
        String description,
        List<ResourceKey<Level>> dimensions,
        Monsters monsters,
        Spawn spawn,
        Structures structures,
        int order
) {
    /** Sentinela de "vanilla de proposito": a staff pode tirar um mundo do perfil global. */
    public static final ResourceLocation NONE_ID = ResourceLocation.fromNamespaceAndPath("aurorion_mundos", "nenhum");
    public static final WorldProfile NONE = new WorldProfile(NONE_ID, "Vanilla", List.of(),
            Monsters.VANILLA, Spawn.VANILLA, Structures.VANILLA, Integer.MIN_VALUE);

    public static Codec<WorldProfile> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("descricao", "").forGetter(WorldProfile::description),
                ResourceKey.codec(Registries.DIMENSION).listOf().optionalFieldOf("dimensoes", List.of())
                        .forGetter(WorldProfile::dimensions),
                Monsters.CODEC.optionalFieldOf("monstros", Monsters.VANILLA).forGetter(WorldProfile::monsters),
                Spawn.CODEC.optionalFieldOf("spawn", Spawn.VANILLA).forGetter(WorldProfile::spawn),
                Structures.CODEC.optionalFieldOf("estruturas", Structures.VANILLA).forGetter(WorldProfile::structures),
                Codec.INT.optionalFieldOf("ordem", 0).forGetter(WorldProfile::order)
        ).apply(instance, (description, dimensions, monsters, spawn, structures, order) ->
                new WorldProfile(id, description, dimensions, monsters, spawn, structures, order)));
    }

    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Os monstros que nascem (ou carregam) no mundo. Multiplicadores: 1 e o vanilla. Somas: 0 e o
     * vanilla.
     *
     * @param health      multiplica a vida maxima
     * @param damage      multiplica todo dano que um monstro causa — golpe, flecha, bola de fogo, explosao
     * @param armor       pontos de armadura somados
     * @param toughness   resistencia de armadura somada
     * @param knockback   resistencia a empurrao somada (0..1)
     * @param speed       multiplica a velocidade de movimento
     * @param followRange multiplica o alcance em que o monstro percebe e persegue
     * @param experience  multiplica a experiencia que o monstro deixa
     * @param bosses      tambem vale para chefe (tag {@code c:bosses})
     * @param spawners    tambem vale para monstro de spawner
     */
    public record Monsters(double health, double damage, double armor, double toughness, double knockback,
                           double speed, double followRange, double experience, boolean bosses, boolean spawners) {
        public static final Monsters VANILLA = new Monsters(1, 1, 0, 0, 0, 1, 1, 1, false, true);

        public static final Codec<Monsters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.05, 1000).optionalFieldOf("vida", 1.0).forGetter(Monsters::health),
                Codec.doubleRange(0, 1000).optionalFieldOf("dano", 1.0).forGetter(Monsters::damage),
                Codec.doubleRange(-30, 30).optionalFieldOf("armadura", 0.0).forGetter(Monsters::armor),
                Codec.doubleRange(-20, 20).optionalFieldOf("resistenciaArmadura", 0.0).forGetter(Monsters::toughness),
                Codec.doubleRange(-1, 1).optionalFieldOf("resistenciaEmpurrao", 0.0).forGetter(Monsters::knockback),
                Codec.doubleRange(0.1, 5).optionalFieldOf("velocidade", 1.0).forGetter(Monsters::speed),
                Codec.doubleRange(0.1, 5).optionalFieldOf("alcance", 1.0).forGetter(Monsters::followRange),
                Codec.doubleRange(0, 1000).optionalFieldOf("experiencia", 1.0).forGetter(Monsters::experience),
                Codec.BOOL.optionalFieldOf("chefes", false).forGetter(Monsters::bosses),
                Codec.BOOL.optionalFieldOf("spawners", true).forGetter(Monsters::spawners)
        ).apply(instance, Monsters::new));

        /** Nada a fazer nos atributos: o caso de todo mundo sem perfil sai aqui. */
        public boolean attributesVanilla() {
            return health == 1 && armor == 0 && toughness == 0 && knockback == 0 && speed == 1 && followRange == 1;
        }
    }

    /**
     * Quantos cabem. Multiplica o teto de criaturas do vanilla — o global da dimensao e o local, em
     * volta de cada jogador — para a categoria.
     *
     * @param monsterCap  monstros ({@code MobCategory.MONSTER}); 2 = o dobro de monstros vivos ao mesmo tempo
     * @param creatureCap animais ({@code MobCategory.CREATURE})
     */
    public record Spawn(double monsterCap, double creatureCap) {
        public static final Spawn VANILLA = new Spawn(1, 1);

        public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0, 10).optionalFieldOf("tetoMonstros", 1.0).forGetter(Spawn::monsterCap),
                Codec.doubleRange(0, 10).optionalFieldOf("tetoCriaturas", 1.0).forGetter(Spawn::creatureCap)
        ).apply(instance, Spawn::new));
    }

    /**
     * Quantas estruturas o terreno tem. Lido <b>uma vez, quando a dimensao sobe</b>: o posicionamento de
     * estrutura e calculado na criacao do mundo, e muda-lo com o servidor no ar deixaria chunks ja
     * gerados com uma regra e os novos com outra. Trocar exige reiniciar, e so vale para chunk novo.
     *
     * @param density multiplica quantas estruturas cabem por area (2 = o dobro; 0,5 = metade)
     * @param sets    multiplicador por conjunto de estrutura ({@code minecraft:villages},
     *                {@code minecraft:pillager_outposts}, conjuntos de mod...), por cima de {@code density}
     */
    public record Structures(double density, Map<ResourceLocation, Double> sets) {
        public static final Structures VANILLA = new Structures(1, Map.of());

        public static final Codec<Structures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.05, 16).optionalFieldOf("densidade", 1.0).forGetter(Structures::density),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.doubleRange(0.05, 16))
                        .optionalFieldOf("conjuntos", Map.of()).forGetter(Structures::sets)
        ).apply(instance, Structures::new));

        public boolean isVanilla() {
            return density == 1 && sets.isEmpty();
        }

        public double densityFor(ResourceLocation set) {
            Double specific = sets.get(set);
            return specific == null ? density : density * specific;
        }
    }
}

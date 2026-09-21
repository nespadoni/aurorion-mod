package com.aurorion.areas.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import java.util.List;

public record AmbientProfile(ResourceLocation id, Sounds sounds, Pulse pulse, Attack attack, Fog fog,
                             Dread dread, Whispers whispers, Alert alert, Spawns spawns) {
    public record Interval(int min, int max) {
        public static final Codec<Interval> CODEC = RecordCodecBuilder.<Interval>create(i -> i.group(
                Codec.intRange(1, 3600).fieldOf("min_seconds").forGetter(Interval::min),
                Codec.intRange(1, 3600).fieldOf("max_seconds").forGetter(Interval::max)
        ).apply(i, Interval::new)).validate(value -> value.min <= value.max ? DataResult.success(value)
                : DataResult.error(() -> "min_seconds maior que max_seconds"));
        public long next(long now, RandomSource random) { return next(now, random, 1); }
        /**
         * @param scale encurtamento do intervalo conforme o medo cresce ({@link Dread}). Um segundo
         *              e o piso: abaixo disso o evento deixa de ser um susto e vira um zumbido.
         */
        public long next(long now, RandomSource random, float scale) {
            long ticks = (long) (20L * (min + random.nextInt(max - min + 1)) * scale);
            return now + Math.max(20L, ticks);
        }
    }
    public record Sounds(List<ResourceLocation> events, Interval interval, float volume, float pitch, int distance) {
        public static final Sounds NONE = new Sounds(List.of(), new Interval(8, 18), .5F, .8F, 7);
        public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf(0, 32).fieldOf("events").forGetter(Sounds::events),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Sounds::interval),
                Codec.floatRange(.01F, 2).optionalFieldOf("volume", .5F).forGetter(Sounds::volume),
                Codec.floatRange(.5F, 2).optionalFieldOf("pitch", .8F).forGetter(Sounds::pitch),
                Codec.intRange(2, 16).optionalFieldOf("distance", 7).forGetter(Sounds::distance)
        ).apply(i, Sounds::new));
        public Sounds { events = List.copyOf(events); }
    }
    /**
     * @param vignette sombra que fecha pelas bordas da tela. Vale desde a entrada.
     * @param blackout apagao de tela cheia <b>no auge do medo</b>: 0 na entrada e este valor quando
     *                 {@link Dread} chega a 1. E o unico campo do pulso que nasce do medo em vez de
     *                 so ser somado por ele — um apagao total logo na entrada nao assusta ninguem,
     *                 so tira a pessoa do jogo. Em 1 a tela fica preta por inteiro, HUD incluido.
     * @param blackoutSeconds quanto o apagao dura, <b>independente</b> do Darkness e da cegueira.
     *                        Ele e um golpe, nao um estado: quinze segundos de tela preta com
     *                        criatura em cima deixa de ser susto e vira impossibilidade de jogar.
     *                        Passados estes segundos a tela volta para a escuridao pesada do pulso,
     *                        que continua correndo. Limitado ao que sobra do pulso.
     */
    public record Pulse(int darknessSeconds, int blindnessSeconds, float vignette, float blackout,
                        int blackoutSeconds, Interval interval) {
        public static final Pulse NONE = new Pulse(0, 0, 0, 0, 4, new Interval(20, 40));
        public static final Codec<Pulse> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 30).optionalFieldOf("darkness_seconds", 0).forGetter(Pulse::darknessSeconds),
                Codec.intRange(0, 15).optionalFieldOf("blindness_seconds", 0).forGetter(Pulse::blindnessSeconds),
                Codec.floatRange(0, .9F).optionalFieldOf("vignette", 0F).forGetter(Pulse::vignette),
                Codec.floatRange(0, 1).optionalFieldOf("blackout", 0F).forGetter(Pulse::blackout),
                Codec.intRange(1, 30).optionalFieldOf("blackout_seconds", NONE.blackoutSeconds).forGetter(Pulse::blackoutSeconds),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Pulse::interval)
        ).apply(i, Pulse::new));
    }
    public record Attack(float damage, boolean lethal, Interval interval) {
        public static final Attack NONE = new Attack(0, false, new Interval(30, 60));
        public static final Codec<Attack> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 10).optionalFieldOf("damage", 0F).forGetter(Attack::damage),
                Codec.BOOL.optionalFieldOf("lethal", false).forGetter(Attack::lethal),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Attack::interval)
        ).apply(i, Attack::new));
    }
    public record Fog(float distance, int color) {
        public static final Fog NONE = new Fog(0, 0x0D1715);
        public static final Codec<Fog> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 256).optionalFieldOf("distance", 0F).forGetter(Fog::distance),
                COLOR.optionalFieldOf("color", NONE.color).forGetter(Fog::color)
        ).apply(i, Fog::new));
    }

    /**
     * Quanto mais tempo dentro, pior fica.
     *
     * <p>Tudo aqui e <b>multiplicador do que ja esta configurado</b>, nunca um segundo conjunto de
     * valores: o ambiente continua sendo descrito uma vez em {@code sounds}/{@code pulse}/
     * {@code attack}/{@code fog}, e o medo so diz o quanto aquilo aperta no fim da escalada. Por
     * isso {@link #NONE} e neutro em todos os campos — um ambiente que nao declarar {@code dread}
     * se comporta exatamente como antes deste campo existir.
     *
     * <p>O relogio e o tempo dentro do ambiente, nao o tempo de jogo: sair zera, e a proxima entrada
     * comeca do zero de novo.
     *
     * @param rampSeconds       tempo, em segundos, ate o medo chegar ao maximo.
     * @param damageScale       multiplicador do dano do ataque invisivel no maximo.
     * @param intervalScale     fracao do intervalo original no maximo (0,35 = eventos 3x mais frequentes).
     * @param darknessBonus     segundos de Darkness somados ao pulso, no maximo.
     * @param blindnessBonus    segundos de cegueira somados ao pulso, no maximo.
     * @param vignetteBonus     sombra periferica somada ao pulso, no maximo.
     * @param fogScale          fracao da distancia de neblina no maximo (0,35 = neblina bem mais fechada).
     * @param lethalAfterSeconds a partir daqui o ataque passa a poder matar; {@code 0} nunca mata.
     */
    public record Dread(int rampSeconds, float damageScale, float intervalScale, int darknessBonus,
                        int blindnessBonus, float vignetteBonus, float fogScale, int lethalAfterSeconds) {
        public static final Dread NONE = new Dread(60, 1, 1, 0, 0, 0, 1, 0);
        public static final Codec<Dread> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 3600).optionalFieldOf("ramp_seconds", NONE.rampSeconds).forGetter(Dread::rampSeconds),
                Codec.floatRange(1, 20).optionalFieldOf("damage_scale", 1F).forGetter(Dread::damageScale),
                Codec.floatRange(.1F, 1).optionalFieldOf("interval_scale", 1F).forGetter(Dread::intervalScale),
                Codec.intRange(0, 30).optionalFieldOf("darkness_bonus_seconds", 0).forGetter(Dread::darknessBonus),
                Codec.intRange(0, 15).optionalFieldOf("blindness_bonus_seconds", 0).forGetter(Dread::blindnessBonus),
                Codec.floatRange(0, .9F).optionalFieldOf("vignette_bonus", 0F).forGetter(Dread::vignetteBonus),
                Codec.floatRange(.05F, 1).optionalFieldOf("fog_scale", 1F).forGetter(Dread::fogScale),
                Codec.intRange(0, 3600).optionalFieldOf("lethal_after_seconds", 0).forGetter(Dread::lethalAfterSeconds)
        ).apply(i, Dread::new));

        /** 0 ao entrar, 1 depois de {@code rampSeconds} la dentro. */
        public float level(long ticksInside) {
            return Mth.clamp(ticksInside / (rampSeconds * 20F), 0, 1);
        }
        /** Interpola de 1 (na entrada) ate {@code target} (no auge). */
        public float ramp(float target, float level) { return 1 + (target - 1) * level; }
        public boolean lethalNow(long ticksInside) {
            return lethalAfterSeconds > 0 && ticksInside >= 20L * lethalAfterSeconds;
        }
    }

    /**
     * A voz da propria pessoa, na barra de acao: frases curtas que aparecem sozinhas enquanto ela
     * continua ali dentro.
     *
     * <p>As frases sao <b>texto literal do datapack</b>, e nao chaves de traducao, pelo mesmo motivo
     * que os sons sao ids soltos: escrever uma frase nova e editar o JSON e dar {@code /reload}, sem
     * passar por arquivo de idioma nem por recompilacao. O servidor manda o texto pronto, entao ele
     * aparece igual inclusive para quem nao tem o modulo cliente.
     *
     * @param color cor do inicio da escalada; ela escorrega para {@code peakColor} conforme o medo sobe.
     */
    public record Whispers(List<String> lines, Interval interval, int color, int peakColor) {
        public static final Whispers NONE = new Whispers(List.of(), new Interval(25, 55), 0x8A7F9B, 0x8A2B2B);
        /**
         * A frase e validada <b>no elemento</b>, e nao so no construtor do record: o DFU nao captura
         * {@code RuntimeException} de dentro de um {@code RecordCodecBuilder}, entao uma frase em
         * branco no JSON estouraria no meio do {@code /reload} em vez de virar uma linha de log e um
         * arquivo ignorado, que e o contrato do {@code DatapackRegistry}. O construtor mantem a mesma
         * checagem para quem monta um {@code Whispers} em codigo.
         */
        private static final Codec<String> LINE = Codec.STRING.validate(line ->
                line.isBlank() || line.length() > 96
                        ? DataResult.error(() -> "Frase de sussurro deve ter 1 a 96 caracteres: '" + line + "'")
                        : DataResult.success(line));
        public static final Codec<Whispers> CODEC = RecordCodecBuilder.create(i -> i.group(
                LINE.listOf(0, 32).fieldOf("lines").forGetter(Whispers::lines),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Whispers::interval),
                COLOR.optionalFieldOf("color", NONE.color).forGetter(Whispers::color),
                COLOR.optionalFieldOf("peak_color", NONE.peakColor).forGetter(Whispers::peakColor)
        ).apply(i, Whispers::new));
        public Whispers {
            lines = List.copyOf(lines);
            for (String line : lines) {
                if (line.isBlank() || line.length() > 96) {
                    throw new IllegalArgumentException("Frase de sussurro deve ter 1 a 96 caracteres.");
                }
            }
        }
        /** Mistura canal a canal: no auge do medo a frase chega na cor de pico. */
        public int colorAt(float level) {
            return blend(color >> 16 & 255, peakColor >> 16 & 255, level) << 16
                    | blend(color >> 8 & 255, peakColor >> 8 & 255, level) << 8
                    | blend(color & 255, peakColor & 255, level);
        }
        private static int blend(int from, int to, float level) {
            return Mth.clamp(Math.round(from + (to - from) * level), 0, 255);
        }
    }

    /**
     * O que nasce em volta de quem fica.
     *
     * <p>Nao substitui o spawn natural nem mexe nele: sao criaturas colocadas <b>perto de um jogador
     * especifico</b>, porque ele esta ali ha tempo demais. Quando ele vai embora, elas somem pelo
     * despawn normal do vanilla — nenhuma delas e marcada como persistente, entao a floresta nao
     * acumula bicho entre uma visita e outra.
     *
     * <p>A regra {@code monstros} da area continua mandando: se ela nega spawn hostil, o
     * {@code EntityJoinLevelEvent} do proprio modulo recusa estas criaturas junto com as naturais,
     * sem uma linha de codigo a mais aqui. Os multiplicadores de vida e dano da area tambem valem
     * para elas.
     *
     * @param minDistance      nunca nasce em cima do jogador; ele tem que ouvir antes de ver.
     * @param maxNearby        teto de criaturas <b>nossas</b> vivas em volta daquele jogador. E o que
     *                         impede uma permanencia longa de virar um exercito, e o que limita o
     *                         custo num servidor cheio.
     * @param count            quantas por leva na entrada.
     * @param peakCount        quantas por leva no auge do medo.
     * @param startAfterSeconds silencio inicial: entrar e sair nao faz nascer nada.
     * @param huntFrom         nivel de medo a partir do qual o que nasce ja vem sabendo onde voce
     *                         esta. Abaixo disso a criatura aparece e procura sozinha.
     */
    public record Spawns(List<Entry> entries, Interval interval, int minDistance, int maxDistance,
                         int maxNearby, int count, int peakCount, int startAfterSeconds, float huntFrom) {
        public static final Spawns NONE = new Spawns(List.of(), new Interval(30, 60), 8, 22, 0, 1, 1, 60, 1);

        /**
         * @param from  medo minimo para esta criatura poder aparecer. E o que faz a floresta piorar
         *              de <em>tipo</em>, e nao so de quantidade: o que nasce aos seis minutos nao e
         *              o mesmo que nasce ao entrar.
         * @param flying nasce no ar acima do jogador, em vez de procurar chao.
         */
        public record Entry(ResourceLocation id, int weight, float from, boolean flying) {
            public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(Entry::id),
                    Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(Entry::weight),
                    Codec.floatRange(0, 1).optionalFieldOf("from", 0F).forGetter(Entry::from),
                    Codec.BOOL.optionalFieldOf("flying", false).forGetter(Entry::flying)
            ).apply(i, Entry::new));
        }

        public static final Codec<Spawns> CODEC = RecordCodecBuilder.<Spawns>create(i -> i.group(
                Entry.CODEC.listOf(0, 32).fieldOf("entities").forGetter(Spawns::entries),
                Interval.CODEC.optionalFieldOf("interval", NONE.interval).forGetter(Spawns::interval),
                Codec.intRange(2, 64).optionalFieldOf("min_distance", NONE.minDistance).forGetter(Spawns::minDistance),
                Codec.intRange(2, 64).optionalFieldOf("max_distance", NONE.maxDistance).forGetter(Spawns::maxDistance),
                Codec.intRange(0, 24).optionalFieldOf("max_nearby", NONE.maxNearby).forGetter(Spawns::maxNearby),
                Codec.intRange(0, 8).optionalFieldOf("count", NONE.count).forGetter(Spawns::count),
                Codec.intRange(0, 8).optionalFieldOf("peak_count", NONE.peakCount).forGetter(Spawns::peakCount),
                Codec.intRange(0, 3600).optionalFieldOf("start_after_seconds", NONE.startAfterSeconds).forGetter(Spawns::startAfterSeconds),
                Codec.floatRange(0, 1).optionalFieldOf("hunt_from", NONE.huntFrom).forGetter(Spawns::huntFrom)
        ).apply(i, Spawns::new)).validate(value -> value.minDistance <= value.maxDistance
                ? DataResult.success(value)
                : DataResult.error(() -> "min_distance maior que max_distance"));

        public Spawns { entries = List.copyOf(entries); }

        /** Quantas nascem nesta leva, interpolando de {@code count} ate {@code peakCount}. */
        public int countAt(float level) { return Math.round(count + (peakCount - count) * level); }
    }

    /**
     * Aviso a quem modera quando alguem entra, sai, ou cruza o ponto em que o lugar passa a matar.
     *
     * <p>Mora no <b>ambiente</b>, e nao na area: quem e perigoso e o ambiente, e uma staff que criar
     * tres manchas de floresta nao deveria precisar ligar o aviso tres vezes. A area entra so no
     * texto da mensagem, como nome do lugar.
     *
     * <p>Os tres campos sao {@code true} por padrao, mas o bloco inteiro e {@link #NONE} quando
     * ausente: declarar {@code "alert": {}} liga tudo, e ambiente que nao declara o bloco nao avisa
     * nada — que e como todos os ambientes se comportavam antes deste campo existir.
     *
     * @param cooldownSeconds silencio minimo entre dois avisos de entrada do <b>mesmo jogador</b>.
     *                        Sem ele, quem fica em cima da linha da fronteira vira uma metralhadora
     *                        no chat de quem modera.
     */
    public record Alert(boolean enter, boolean exit, boolean lethal, int cooldownSeconds) {
        public static final Alert NONE = new Alert(false, false, false, 120);
        public static final Codec<Alert> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enter", true).forGetter(Alert::enter),
                Codec.BOOL.optionalFieldOf("exit", true).forGetter(Alert::exit),
                Codec.BOOL.optionalFieldOf("lethal", true).forGetter(Alert::lethal),
                Codec.intRange(0, 3600).optionalFieldOf("cooldown_seconds", NONE.cooldownSeconds)
                        .forGetter(Alert::cooldownSeconds)
        ).apply(i, Alert::new));
    }

    /** "#RRGGBB" — a mesma forma que quem edita datapack ja escreve nas cores das casas. */
    private static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(s -> {
        try {
            if (!s.matches("#[0-9a-fA-F]{6}")) return DataResult.error(() -> "Cor deve ser #RRGGBB.");
            return DataResult.success(Integer.parseInt(s.substring(1), 16));
        } catch (RuntimeException e) { return DataResult.error(() -> "Cor invalida."); }
    }, c -> String.format(java.util.Locale.ROOT, "#%06X", c));

    public static Codec<AmbientProfile> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(i -> i.group(
                Sounds.CODEC.optionalFieldOf("sounds", Sounds.NONE).forGetter(AmbientProfile::sounds),
                Pulse.CODEC.optionalFieldOf("pulse", Pulse.NONE).forGetter(AmbientProfile::pulse),
                Attack.CODEC.optionalFieldOf("attack", Attack.NONE).forGetter(AmbientProfile::attack),
                Fog.CODEC.optionalFieldOf("fog", Fog.NONE).forGetter(AmbientProfile::fog),
                Dread.CODEC.optionalFieldOf("dread", Dread.NONE).forGetter(AmbientProfile::dread),
                Whispers.CODEC.optionalFieldOf("whispers", Whispers.NONE).forGetter(AmbientProfile::whispers),
                Alert.CODEC.optionalFieldOf("alert", Alert.NONE).forGetter(AmbientProfile::alert),
                Spawns.CODEC.optionalFieldOf("spawns", Spawns.NONE).forGetter(AmbientProfile::spawns)
        ).apply(i, (sounds, pulse, attack, fog, dread, whispers, alert, spawns) ->
                new AmbientProfile(id, sounds, pulse, attack, fog, dread, whispers, alert, spawns)));
    }
}

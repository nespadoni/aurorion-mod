package com.aurorion.ethereal.ceremony;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uma cerimonia em andamento. Vive so na memoria: se o servidor cair no meio, o jogador refaz — e o
 * que se perde e um minuto de perguntas, nao um dado. O que <b>precisa</b> sobreviver e o veredito
 * do fim, e esse vai para o disco ({@link CeremonyData}).
 *
 * <p>As perguntas sao <b>fotografadas na abertura</b>, nao lidas do catalogo a cada resposta: um
 * {@code /reload} no meio de uma cerimonia trocaria o conjunto sob os pes do jogador, e a resposta 3
 * cairia numa pergunta que ele nunca leu.
 */
public final class ActiveCeremony {
    private final UUID player;
    @Nullable
    private final UUID conductor;
    private final List<CeremonyQuestion> questions;
    private final List<Integer> answers;

    ActiveCeremony(UUID player, @Nullable UUID conductor, List<CeremonyQuestion> questions) {
        this.player = player;
        this.conductor = conductor;
        this.questions = List.copyOf(questions);
        this.answers = new ArrayList<>(questions.size());
    }

    public UUID player() {
        return player;
    }

    /** Quem conduz a cerimonia e recebe cada resposta ao vivo, se alguem a iniciou por comando. */
    @Nullable
    public UUID conductor() {
        return conductor;
    }

    public List<CeremonyQuestion> questions() {
        return questions;
    }

    /** Indice da proxima pergunta a ser respondida. */
    public int currentIndex() {
        return answers.size();
    }

    public boolean isFinished() {
        return answers.size() >= questions.size();
    }

    /**
     * Registra a resposta da pergunta {@code index}.
     *
     * <p>O indice vem no pacote e e conferido contra o estado do servidor de proposito: sem isso, um
     * cliente adulterado responderia a pergunta 1 oito vezes, ou pularia direto para a ultima.
     *
     * @return a opcao escolhida, ou {@code null} se o pacote nao bate com o estado atual.
     */
    @Nullable
    public CeremonyQuestion.Option answer(int index, int option) {
        if (isFinished() || index != currentIndex()) {
            return null;
        }
        CeremonyQuestion question = questions.get(index);
        if (option < 0 || option >= question.options().size()) {
            return null;
        }
        answers.add(option);
        return question.options().get(option);
    }

    /** Quantos pontos cada casa recebeu das respostas dadas ate agora. */
    public Map<ResourceLocation, Integer> tally() {
        Map<ResourceLocation, Integer> tally = new HashMap<>();
        for (int i = 0; i < answers.size(); i++) {
            CeremonyQuestion question = questions.get(i);
            CeremonyQuestion.Option option = question.options().get(answers.get(i));
            tally.merge(option.house(), question.weight(), Integer::sum);
        }
        return tally;
    }

    /** Uma linha por resposta, para a staff ler o caminho que levou ao resultado. */
    public List<String> summary() {
        List<String> lines = new ArrayList<>(answers.size());
        for (int i = 0; i < answers.size(); i++) {
            CeremonyQuestion question = questions.get(i);
            CeremonyQuestion.Option option = question.options().get(answers.get(i));
            lines.add((i + 1) + ". " + option.text().getString() + "  ->  " + option.house().getPath());
        }
        return lines;
    }
}

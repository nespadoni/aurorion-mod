package com.aurorion.intencao.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.core.client.menu.MenuUi;
import com.aurorion.intencao.network.DuelPayloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * O duelo dentro do menu do personagem (registro do core, desenhado pelo {@code aurorion-menu}):
 * <ul>
 *   <li><b>Ações</b> — só na sua vez: atacar, defender, técnicas, magias e desistir, com o alvo quando há
 *       mais de um inimigo;</li>
 *   <li><b>Duelo</b> — o estado do duelo aberto e desistir, ou desafiar quem está perto;</li>
 *   <li>a interação "Desafiar para duelo" sobre um jogador, com a lista de limites.</li>
 * </ul>
 * O convite chega como a janela ○/× do menu. Tudo é montado na hora a partir do último {@code State} e
 * {@code Prompt}; nada roda por tick.
 */
final class DuelSections {
    static final String ACTIONS = "acoes";
    static final String DUEL = "duelo";
    private static final double NEAR = 8;
    /** Mesma lista do comando ({@code DuelManager.LIMITS}), com o texto que a pessoa vê. */
    private static final String[][] LIMITS = {
            {"primeiro_sangue", "Primeiro sangue"}, {"75", "Até 75% da vida"}, {"50", "Até 50% da vida"},
            {"25", "Até 25% da vida"}, {"10", "Até 10% da vida"}, {"morte", "ATÉ A MORTE"}};

    private DuelSections() {
    }

    static void register() {
        CharacterMenu.register(new Actions());
        CharacterMenu.register(new Duel());
        CharacterMenu.registerInteraction(new CharacterMenu.Interaction("duelo", 10, DuelSections::challengeEntry));
    }

    /** A janela do convite: ○ aceita, × recusa (ou o prazo acaba). */
    static CharacterMenu.Modal invite(DuelPayloads.Invite invite) {
        String body = invite.challenger() + " desafia você para um duelo, " + invite.limit() + "."
                + (invite.death() ? " Quem zerar a vida da ficha perde o personagem para sempre." : "")
                + " Aponte e clique: ○ aceita, × recusa.";
        return new CharacterMenu.Modal("DUELO", body, invite.death() ? 0xB3261E : MenuUi.INK,
                () -> command("duelo aceitar"), () -> command("duelo recusar"), invite.seconds());
    }

    // --- Ações (a sua vez) -------------------------------------------------------------------------

    private static final class Actions implements CharacterMenu.Section {
        @Override
        public String id() {
            return ACTIONS;
        }

        @Override
        public String title() {
            return "Ações";
        }

        @Override
        public String icon() {
            return "sword";
        }

        @Override
        public int order() {
            return 0;
        }

        @Override
        public boolean visible() {
            DuelPayloads.State state = ClientDuel.state;
            return ClientDuel.myTurn() && state != null && !state.paused();
        }

        @Override
        public List<CharacterMenu.Entry> entries() {
            DuelPayloads.Prompt prompt = ClientDuel.prompt;
            List<CharacterMenu.Entry> out = new ArrayList<>();
            if (prompt == null || !ClientDuel.myTurn()) {
                out.add(CharacterMenu.Entry.disabled("Não é a sua vez", "no"));
                return out;
            }
            List<CharacterMenu.Entry> attacks = new ArrayList<>(), techniques = new ArrayList<>(), spells = new ArrayList<>();
            CharacterMenu.Entry defend = null, surrender = null;
            for (DuelPayloads.Choice choice : prompt.choices()) {
                switch (choice.kind()) {
                    case 0, 1 -> attacks.add(targeted(prompt, choice, choice.kind() == 0 ? "sword" : "bow"));
                    case 2 -> defend = CharacterMenu.Entry.action(choice.label(), "shield", cost(choice), choice.enabled(),
                            () -> choose(choice, -1));
                    case 3 -> techniques.add(targeted(prompt, choice, "star"));
                    case 4 -> spells.add(targeted(prompt, choice, "magic"));
                    default -> surrender = CharacterMenu.Entry.action(choice.label(), "flag", "", choice.enabled(),
                            () -> CharacterMenu.prompt(new CharacterMenu.Modal("DESISTIR", "Desistir do duelo agora?",
                                    MenuUi.INK, () -> command("duelo desistir"), null, 0)));
                }
            }
            if (!attacks.isEmpty()) out.add(group("Atacar", "sword", attacks));
            if (defend != null) out.add(defend);
            if (!techniques.isEmpty()) out.add(group("Técnicas", "star", techniques));
            if (!spells.isEmpty()) out.add(group("Magias", "magic", spells));
            if (surrender != null) out.add(surrender);
            long left = prompt.seconds() - (Util.getMillis() - ClientDuel.promptAt) / 1000;
            if (left > 0) out.add(CharacterMenu.Entry.disabled("Sua vez — " + left + "s", "target"));
            return out;
        }
    }

    /** Ação com alvo: direto quando só há um inimigo, senão abre a lista de alvos. */
    private static CharacterMenu.Entry targeted(DuelPayloads.Prompt prompt, DuelPayloads.Choice choice, String icon) {
        if (!choice.enabled() || prompt.targets().size() <= 1) {
            int target = prompt.targets().isEmpty() ? -1 : prompt.targets().get(0).index();
            return CharacterMenu.Entry.action(choice.label(), icon, cost(choice), choice.enabled(), () -> choose(choice, target));
        }
        Supplier<List<CharacterMenu.Entry>> targets = () -> {
            List<CharacterMenu.Entry> list = new ArrayList<>();
            for (DuelPayloads.Target target : prompt.targets()) {
                list.add(CharacterMenu.Entry.action(target.name(), "target", "", true, () -> choose(choice, target.index())));
            }
            return list;
        };
        return CharacterMenu.Entry.branch(choice.label() + (choice.cost() > 0 ? "  · " + choice.cost() + " PE" : ""),
                icon, true, targets);
    }

    private static CharacterMenu.Entry group(String label, String icon, List<CharacterMenu.Entry> children) {
        boolean any = children.stream().anyMatch(CharacterMenu.Entry::enabled);
        return CharacterMenu.Entry.branch(label, icon, any, () -> children);
    }

    private static String cost(DuelPayloads.Choice choice) {
        return choice.cost() > 0 ? choice.cost() + " PE" : "";
    }

    private static void choose(DuelPayloads.Choice choice, int target) {
        ClientDuel.choose(choice.kind(), choice.index(), target);
        CharacterMenu.close();
    }

    // --- Duelo (estado / desafiar) -----------------------------------------------------------------

    private static final class Duel implements CharacterMenu.Section {
        @Override
        public String id() {
            return DUEL;
        }

        @Override
        public String title() {
            return "Duelo";
        }

        @Override
        public String icon() {
            return "duel";
        }

        @Override
        public int order() {
            return 45;
        }

        @Override
        public List<CharacterMenu.Entry> entries() {
            List<CharacterMenu.Entry> out = new ArrayList<>();
            DuelPayloads.State state = ClientDuel.state;
            if (state != null && ClientDuel.active()) {
                String turn = state.phase() == 0 ? "Começa em " + state.seconds() + "s"
                        : state.paused() ? "Pausado pelo mestre" : "Rodada " + (state.round() + 1);
                out.add(CharacterMenu.Entry.disabled(turn + " · " + limit(state.limit()), "duel"));
                for (DuelPayloads.Fighter f : state.fighters()) {
                    out.add(CharacterMenu.Entry.disabled(f.name() + "  " + f.hp() + "/" + f.hpMax(), "person"));
                }
                out.add(CharacterMenu.Entry.action("Desistir", "flag", "", true,
                        () -> CharacterMenu.prompt(new CharacterMenu.Modal("DESISTIR", "Desistir do duelo agora?",
                                MenuUi.INK, () -> command("duelo desistir"), null, 0))));
                return out;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                for (AbstractClientPlayer other : mc.level.players()) {
                    if (other == mc.player || other.isSpectator() || other.distanceTo(mc.player) > NEAR) continue;
                    String name = other.getGameProfile().getName();
                    out.add(CharacterMenu.Entry.branch("Desafiar " + other.getDisplayName().getString(), "duel", true,
                            () -> limits(name)));
                    if (out.size() >= 8) break;
                }
            }
            if (out.isEmpty()) out.add(CharacterMenu.Entry.disabled("Ninguém por perto para duelar", "no"));
            return out;
        }
    }

    private static String limit(int limit) {
        return limit < 0 ? "primeiro sangue" : limit == 0 ? "até a morte" : "até " + limit + "%";
    }

    private static CharacterMenu.Entry challengeEntry(Player target) {
        if (ClientDuel.active()) return null;
        String name = target.getGameProfile().getName();
        return CharacterMenu.Entry.branch("Desafiar para duelo", "duel", true, () -> limits(name));
    }

    private static List<CharacterMenu.Entry> limits(String name) {
        List<CharacterMenu.Entry> out = new ArrayList<>(LIMITS.length);
        for (String[] limit : LIMITS) {
            boolean death = limit[0].equals("morte");
            Runnable send = () -> {
                command("duelo desafiar " + name + " " + limit[0]);
                CharacterMenu.close();
            };
            out.add(CharacterMenu.Entry.action(limit[1], death ? "heart" : "target", death ? "perde o personagem" : "", true,
                    death ? () -> CharacterMenu.prompt(new CharacterMenu.Modal("ATÉ A MORTE",
                            "Se a sua vida na ficha zerar, o personagem morre de vez. Desafiar " + name + "?",
                            0xB3261E, send, null, 0)) : send));
        }
        return out;
    }

    private static void command(String command) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }
}

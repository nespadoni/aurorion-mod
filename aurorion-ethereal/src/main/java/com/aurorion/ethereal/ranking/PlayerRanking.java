package com.aurorion.ethereal.ranking;

/**
 * Os numeros de um jogador. Imutavel de proposito: cada ajuste devolve um registro novo, entao nao
 * existe o caso de duas partes do mod segurarem a mesma instancia e uma ver o valor da outra.
 */
public record PlayerRanking(String name, int points, int deaths, int duelWins, int missions) {
    public static PlayerRanking of(String name) {
        return new PlayerRanking(name, 0, 0, 0, 0);
    }

    public PlayerRanking withName(String newName) {
        return new PlayerRanking(newName, points, deaths, duelWins, missions);
    }

    public PlayerRanking withPoints(int newPoints) {
        return new PlayerRanking(name, newPoints, deaths, duelWins, missions);
    }

    public PlayerRanking withDeath() {
        return new PlayerRanking(name, points, deaths + 1, duelWins, missions);
    }

    public PlayerRanking withDuelWin() {
        return new PlayerRanking(name, points, deaths, duelWins + 1, missions);
    }

    public PlayerRanking withMission() {
        return new PlayerRanking(name, points, deaths, duelWins, missions + 1);
    }
}

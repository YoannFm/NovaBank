package dev.novadev.novabank.core.application;

import java.util.List;

/**
 * Bilan d'un transfert de banque a banque.
 *
 * @param transferId identifiant du transfert (reference dans le journal de la base)
 * @param lines      lignes de stock transferees en entier
 * @param units      unites transferees
 * @param problems   lignes non transferees ou perdues, avec la cause (vide si tout est passe)
 */
public record BankTransferReport(java.util.UUID transferId, int lines, long units, List<String> problems) {

    /**
     * Copie defensive.
     */
    public BankTransferReport {
        problems = List.copyOf(problems);
    }

    /**
     * @return {@code true} si aucune ligne n'a pose probleme
     */
    public boolean complete() {
        return problems.isEmpty();
    }
}

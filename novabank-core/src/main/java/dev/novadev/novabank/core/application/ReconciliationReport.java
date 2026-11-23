package dev.novadev.novabank.core.application;

/**
 * Bilan de la reconciliation des retraits restes reserves.
 *
 * @param settled retraits soldes
 * @param failed  retraits dont le solde a echoue (re-tentes au prochain demarrage)
 * @param units   unites non remises traitees selon la politique D13
 */
public record ReconciliationReport(int settled, int failed, long units) {
}

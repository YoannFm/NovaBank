package dev.novadev.novabank.core.bank;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Etat immuable d'une banque : proprietaire, mode de valorisation, version et lignes
 * de stock par item.
 *
 * <p>La version sert au verrou optimiste des backends : elle est incrementee par le
 * {@code BankRepository} a chaque mutation enregistree, jamais par les methodes de
 * cette classe. L'epoch de reset compte les reinitialisations ; elle est persistee
 * avec la banque et permet de savoir si un retrait reserve precede un reset. Les lignes vides sont oubliees ; une ligne sans stock mais avec une
 * base non nulle est conservee (sinon redeposer apres un reset regenererait de la valeur).
 *
 * @param id            identifiant de la banque
 * @param owner         entite proprietaire
 * @param valuationMode mode de valorisation, fixe a la creation
 * @param version       version, positive ou nulle
 * @param resetEpoch    nombre de reinitialisations subies, positif ou nul
 * @param lines         lignes de stock par item (copie immuable)
 */
public record Bank(BankId id, EntityRef owner, ValuationMode valuationMode, long version, long resetEpoch,
                   Map<ItemKey, StockLine> lines) {

    /**
     * Construit un etat de banque.
     *
     * @throws IllegalArgumentException si la version ou l'epoch est negative
     */
    public Bank {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(valuationMode, "valuationMode");
        if (version < 0 || resetEpoch < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Version ou epoch negative : {0} / {1}",
                    version, resetEpoch));
        }
        lines = Map.copyOf(lines);
    }

    /**
     * Banque neuve, vide, en version 0 et epoch 0.
     *
     * @param id            identifiant
     * @param owner         proprietaire
     * @param valuationMode mode de valorisation
     * @return la banque vide
     */
    public static Bank open(BankId id, EntityRef owner, ValuationMode valuationMode) {
        return new Bank(id, owner, valuationMode, 0L, 0L, Map.of());
    }

    /**
     * Ligne de stock d'un item.
     *
     * @param item item recherche
     * @return la ligne, ou {@link StockLine#EMPTY} si l'item n'a jamais ete depose
     */
    public StockLine line(ItemKey item) {
        return lines.getOrDefault(item, StockLine.EMPTY);
    }

    /**
     * Stock total, tous items confondus.
     *
     * @return la somme des quantites
     * @throws ArithmeticException si la somme deborde
     */
    public long totalQuantity() {
        long total = 0L;
        for (StockLine line : lines.values()) {
            total = Math.addExact(total, line.quantity());
        }
        return total;
    }

    /**
     * Unites comptees, tous items confondus.
     *
     * @return la somme des unites comptees
     * @throws ArithmeticException si la somme deborde
     */
    public long countedQuantity() {
        long total = 0L;
        for (StockLine line : lines.values()) {
            total = Math.addExact(total, line.countedQuantity());
        }
        return total;
    }

    /**
     * Remplace la ligne d'un item (la version n'est pas modifiee).
     *
     * @param item item concerne
     * @param line nouvelle ligne ; une ligne vide est retiree
     * @return la nouvelle banque
     */
    public Bank withLine(ItemKey item, StockLine line) {
        Map<ItemKey, StockLine> updated = new HashMap<>(lines);
        if (line.isBlank()) {
            updated.remove(item);
        } else {
            updated.put(item, line);
        }
        return new Bank(id, owner, valuationMode, version, resetEpoch, updated);
    }

    /**
     * Reinitialise la valeur de toutes les lignes, sans toucher au stock, et avance
     * l'epoch de reset.
     *
     * @return la nouvelle banque (meme version, epoch + 1)
     */
    public Bank reset() {
        Map<ItemKey, StockLine> updated = new HashMap<>();
        lines.forEach((item, line) -> updated.put(item, line.reset()));
        return new Bank(id, owner, valuationMode, version, Math.addExact(resetEpoch, 1L), updated);
    }

    /**
     * Copie avec une autre version (usage reserve aux implementations de stockage).
     *
     * @param newVersion version a appliquer
     * @return la nouvelle banque
     */
    public Bank withVersion(long newVersion) {
        return new Bank(id, owner, valuationMode, newVersion, resetEpoch, lines);
    }
}

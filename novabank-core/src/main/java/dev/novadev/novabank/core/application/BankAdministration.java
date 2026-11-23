package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Operations d'administration sur la banque d'une entite : consultation, historique et
 * reset. Chaque acces a la base se fait sur l'executeur d'entrees/sorties ; la reponse est
 * livree dans le contexte global (l'administrateur peut etre la console).
 *
 * <p>Une reponse vide signale une base indisponible (ou un executeur sature).
 */
public final class BankAdministration {

    /** Entrees par page d'historique. */
    public static final int HISTORY_PAGE_SIZE = 10;

    private static final System.Logger LOGGER = System.getLogger("NovaBank");

    private final BankLedger ledger;
    private final BankRepository banks;
    private final TaskScheduler scheduler;

    /**
     * @param ledger    service metier
     * @param banks     lecture des banques et du journal
     * @param scheduler planificateur
     */
    public BankAdministration(BankLedger ledger, BankRepository banks, TaskScheduler scheduler) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.banks = Objects.requireNonNull(banks, "banks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Etat de la banque d'une entite.
     *
     * @param owner entite
     * @param done  etat, ou vide si la base est indisponible
     */
    public void view(EntityRef owner, Consumer<Optional<BankSnapshot>> done) {
        query(done, () -> {
            Optional<Bank> bank = banks.findBank(BankId.forOwner(owner));
            return BankSnapshot.of(owner, bank, ledger);
        });
    }

    /**
     * Page du journal de la banque d'une entite.
     *
     * @param owner entite
     * @param page  numero de page, a partir de 1
     * @param done  page, ou vide si la base est indisponible
     * @throws IllegalArgumentException si la page n'est pas positive
     */
    public void history(EntityRef owner, int page, Consumer<Optional<HistoryPage>> done) {
        if (page < 1) {
            throw new IllegalArgumentException(ConsoleText.tr("Page invalide : {0}", page));
        }
        query(done, () -> {
            List<OperationRecord> entries = banks.history(BankId.forOwner(owner), (page - 1) * HISTORY_PAGE_SIZE,
                    HISTORY_PAGE_SIZE + 1);
            boolean hasNext = entries.size() > HISTORY_PAGE_SIZE;
            return new HistoryPage(page, hasNext ? entries.subList(0, HISTORY_PAGE_SIZE) : entries, hasNext);
        });
    }

    /**
     * Reinitialise la valeur de la banque d'une entite (stock conserve), et le journalise.
     *
     * @param owner entite
     * @param actor nom de l'administrateur, pour le journal
     * @param done  resultat (refus {@code UNKNOWN_BANK} si la banque n'existe pas), ou vide
     *              si la base est indisponible
     */
    public void reset(EntityRef owner, String actor, Consumer<Optional<OperationResult>> done) {
        Objects.requireNonNull(actor, "actor");
        query(done, () -> {
            OperationId id = OperationId.random();
            OperationResult result = ledger.reset(id, BankId.forOwner(owner));
            LOGGER.log(System.Logger.Level.INFO,
                    ConsoleText.tr("Reset de la banque {0} par {1} : {2}, valeur effacee {3} (operation {4})",
                            owner, actor, result.status(), result.valueDelta().negate().toPlainString(), id));
            return result;
        });
    }

    /**
     * Transfere TOUT le stock d'une banque vers une autre (fusion ou dissolution de groupe, CDC 3.3) : chaque ligne est retiree
     * de la source puis deposee dans la cible, par des operations journalisees et idempotentes. La banque source n'est jamais
     * supprimee (elle reste, vide, avec son journal). La valeur suit les prix de la cible. Ordre choisi pour ne JAMAIS dupliquer
     * d'items : retrait solde d'abord, depot ensuite ; si le depot est refuse (item retire du catalogue), les unites sont
     * rendues a la source, et si cela echoue aussi le probleme est consigne en SEVERE avec les identifiants d'operation.
     *
     * @param from  banque source
     * @param to    banque cible (creee si besoin)
     * @param mode  mode de valeur d'une banque creee
     * @param actor nom de l'administrateur, pour le journal
     * @param done  bilan, ou vide si la base est indisponible
     */
    public void transfer(EntityRef from, EntityRef to, dev.novadev.novabank.api.valuation.ValuationMode mode, String actor,
                         Consumer<Optional<BankTransferReport>> done) {
        Objects.requireNonNull(actor, "actor");
        query(done, () -> transferNow(from, to, mode, actor));
    }

    private BankTransferReport transferNow(EntityRef from, EntityRef to,
                                           dev.novadev.novabank.api.valuation.ValuationMode mode, String actor) {
        java.util.UUID transferId = java.util.UUID.randomUUID();
        BankId source = BankId.forOwner(from);
        BankId target = BankId.forOwner(to);
        List<String> problems = new java.util.ArrayList<>();
        Optional<Bank> bank = banks.findBank(source);
        if (bank.isEmpty()) {
            problems.add("la banque source n'existe pas");
            return new BankTransferReport(transferId, 0, 0, problems);
        }
        ledger.openBank(target, to, mode);
        int lines = 0;
        long units = 0;
        for (var line : new java.util.TreeMap<>(bank.get().lines()).entrySet()) {
            long quantity = line.getValue().quantity();
            if (quantity <= 0) {
                continue;
            }
            dev.novadev.novabank.api.item.ItemKey item = line.getKey();
            OperationId out = stepId(transferId, item, "out");
            OperationResult reserved = ledger.reserveWithdrawal(out, source, item, quantity, new java.util.UUID(0L, 0L));
            if (!reserved.isEffective()) {
                problems.add(item + " : retrait refuse (" + reserved.status() + ")");
                continue;
            }
            OperationResult confirmed = ledger.confirmWithdrawal(out, quantity);
            OperationId in = stepId(transferId, item, "in");
            OperationResult deposited = ledger.deposit(in, target, item, quantity);
            if (deposited.isEffective()) {
                lines++;
                units += quantity;
                continue;
            }
            OperationId back = stepId(transferId, item, "back");
            OperationResult returned = ledger.deposit(back, source, item, quantity);
            if (returned.isEffective()) {
                problems.add(item + " : depot refuse dans la cible (" + deposited.status() + "), unites rendues a la source");
            } else {
                problems.add(item + " : " + quantity + " unite(s) NON placees (depot cible " + deposited.status()
                        + ", retour source " + returned.status() + ")");
                LOGGER.log(System.Logger.Level.ERROR,
                        ConsoleText.tr("Transfert {0} : {1} x {2} retirees de {3} mais ni deposees dans {4} ni rendues (operations {5}, {6}, {7}, retrait solde {8})",
                                transferId, quantity, item, from, to, out, in, back, confirmed.status()));
            }
        }
        LOGGER.log(System.Logger.Level.INFO,
                ConsoleText.tr("Transfert {0} de {1} vers {2} par {3} : {4} ligne(s), {5} unite(s), {6} probleme(s)",
                        transferId, from, to, actor, lines, units, problems.size()));
        return new BankTransferReport(transferId, lines, units, problems);
    }

    private static OperationId stepId(java.util.UUID transferId, dev.novadev.novabank.api.item.ItemKey item, String step) {
        return new OperationId(java.util.UUID.nameUUIDFromBytes(
                ("novabank:transfer:" + transferId + ":" + item + ":" + step).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private <T> void query(Consumer<Optional<T>> done, Supplier<T> read) {
        Objects.requireNonNull(done, "done");
        try {
            scheduler.runAsync(() -> {
                Optional<T> result;
                try {
                    result = Optional.of(read.get());
                } catch (RuntimeException failure) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            ConsoleText.tr("Operation d'administration impossible : {0}",
                                    failure));
                    result = Optional.empty();
                }
                Optional<T> response = result;
                scheduler.runGlobal(() -> done.accept(response));
            });
        } catch (RejectedExecutionException saturated) {
            done.accept(Optional.empty());
        }
    }
}

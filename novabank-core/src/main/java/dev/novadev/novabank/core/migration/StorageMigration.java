package dev.novadev.novabank.core.migration;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Copie tout le contenu d'un backend dans un autre puis verifie la copie (CDC 7.2).
 *
 * <ol>
 *   <li>la cible doit etre vide, sinon refus sans aucune ecriture ;</li>
 *   <li>copie par lots : banques (version, epoch de reset et lignes a l'identique), journal
 *       d'operations complet, livraisons (quantite remise et ordre conserves), banques actives ;
 *       le resume de la source est calcule pendant la lecture ;</li>
 *   <li>verification : relecture complete de la cible et comparaison des resumes (nombres
 *       d'elements, somme de controle de chaque banque, sommes globales).</li>
 * </ol>
 *
 * <p>La source n'est que lue. L'appelant garantit qu'aucune ecriture n'a lieu sur la source
 * pendant la migration (maintenance). En cas d'echec, la cible peut contenir une copie partielle :
 * le message le precise.
 */
public final class StorageMigration {

    /** Nombre d'elements ecrits par lot. */
    public static final int BATCH_SIZE = 500;

    private static final long PROGRESS_STEP = 10_000L;

    private final MigrationListener listener;
    private final Clock clock;

    /**
     * @param listener progression
     * @param clock    horloge de la duree
     */
    public StorageMigration(MigrationListener listener, Clock clock) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param source contenu a copier (lu seulement)
     * @param target contenu de destination, vide
     * @return le bilan de la copie verifiee
     * @throws MigrationException si la cible n'est pas vide, si une lecture ou une ecriture echoue,
     *                            ou si la verification constate un ecart
     */
    public MigrationReport migrate(StorageContent source, StorageContent target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Instant start = clock.instant();
        boolean empty;
        try {
            empty = target.isEmpty();
        } catch (StorageException failure) {
            throw new MigrationException(ConsoleText.tr("Cible inaccessible : {0} (rien n'a ete copie)",
                    failure.getMessage()), failure);
        }
        if (!empty) {
            throw new MigrationException(
                    ConsoleText.tr("La cible n'est pas vide : migration refusee, rien n'a ete copie (videz la base ou le dossier cible)"));
        }
        ContentSummary copied = new ContentSummary();
        try {
            copy("banques", source::exportBanks, copied::addBank, target::importBanks);
            copy("operations", source::exportOperations, copied::addOperation, target::importOperations);
            copy("livraisons", source::exportDeliveries, copied::addDelivery, target::importDeliveries);
            copy("banques actives", source::exportActiveBanks, copied::addActiveBank, target::importActiveBanks);
        } catch (StorageException | IllegalStateException failure) {
            throw new MigrationException(
                    ConsoleText.tr("Copie interrompue : {0} ; la cible contient une copie partielle a vider avant toute nouvelle tentative",
                            failure.getMessage()), failure);
        }
        listener.progress("Verification de la copie (relecture complete de la cible)");
        ContentSummary written;
        try {
            written = ContentSummary.of(target);
        } catch (StorageException | IllegalStateException failure) {
            throw new MigrationException(
                    ConsoleText.tr("Verification impossible : {0} ; la cible contient une copie non verifiee a vider",
                            failure.getMessage()), failure);
        }
        List<String> differences = copied.differences(written);
        if (!differences.isEmpty()) {
            throw new MigrationException(
                    ConsoleText.tr("Verification en echec : {0} ; la cible contient une copie incorrecte a vider",
                            String.join(" ; ", differences)));
        }
        listener.progress("Verification de la source (aucune ecriture pendant la copie)");
        List<String> sourceChanges;
        try {
            sourceChanges = copied.differences(ContentSummary.of(source), "copiees", "a la source maintenant");
        } catch (StorageException | IllegalStateException failure) {
            throw new MigrationException(
                    ConsoleText.tr("Relecture de la source impossible : {0} ; la cible contient une copie non verifiee a vider",
                            failure.getMessage()), failure);
        }
        if (!sourceChanges.isEmpty()) {
            throw new MigrationException(
                    ConsoleText.tr("La source a change pendant la copie ({0}) ; la cible contient une copie perimee a vider",
                            String.join(" ; ", sourceChanges)));
        }
        return new MigrationReport(copied, Duration.between(start, clock.instant()));
    }

    private <T> void copy(String label, Consumer<Consumer<T>> exporter, Consumer<T> summary,
                          Consumer<List<T>> importer) {
        listener.progress("Copie des " + label);
        List<T> batch = new ArrayList<>(BATCH_SIZE);
        long[] count = {0L};
        exporter.accept(element -> {
            summary.accept(element);
            batch.add(element);
            if (batch.size() == BATCH_SIZE) {
                importer.accept(List.copyOf(batch));
                batch.clear();
            }
            count[0]++;
            if (count[0] % PROGRESS_STEP == 0) {
                listener.progress(count[0] + " " + label + " copiees");
            }
        });
        if (!batch.isEmpty()) {
            importer.accept(List.copyOf(batch));
        }
        listener.progress(count[0] + " " + label + " copiees au total");
    }
}

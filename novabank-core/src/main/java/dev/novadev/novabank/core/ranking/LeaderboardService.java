package dev.novadev.novabank.core.ranking;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.core.application.ScheduledTask;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.LocalChangeSink;
import dev.novadev.novabank.core.sync.RemoteChangeListener;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classements par type d'entite, tenus en memoire.
 *
 * <p>Source de verite : le stockage. Le cache est mis a jour de trois facons :
 * <ul>
 *   <li>incrementale : chaque changement local ou distant ({@link LocalChangeSink},
 *       {@link RemoteChangeListener}) recalcule la seule banque concernee, sur l'executeur IO,
 *       avec fusion des demandes repetees ;</li>
 *   <li>complete : relecture de toutes les banques au demarrage puis a chaque periode
 *       ({@link #start}), qui rattrape tout ecart (prix modifies, banque supprimee, evenement
 *       manque) ;</li>
 *   <li>a la demande : {@link #requestRebuild()} apres un rechargement de la configuration.</li>
 * </ul>
 * Les classements ne sont donc jamais plus anciens que la periode de recalcul, et en pratique que
 * le delai de propagation des changements.
 *
 * <p>Sur un serveur de base de donnees partage, la valeur d'une banque est recalculee depuis son
 * stock, au catalogue de prix LOCAL du serveur. Seules les banques de valeur strictement positive
 * sont classees. Sure entre threads.
 */
public final class LeaderboardService implements LocalChangeSink, RemoteChangeListener, AutoCloseable {

    private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::value).reversed()
            .thenComparing(entry -> entry.bank.owner().type()).thenComparing(entry -> entry.bank.owner().id())
            .thenComparing(entry -> entry.bank.id().toString());

    private record Entry(Bank bank, BigDecimal value) {
    }

    private final BankRepository banks;
    private final StorageContent content;
    private final BiFunction<Bank, String, BigDecimal> valuer;
    private final Supplier<Map<String, Integer>> enabledTypes;
    private final Clock clock;
    private final Logger logger;
    private final Set<BankId> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean rebuildQueued = new AtomicBoolean();
    private final AtomicReference<Map<String, Map<BankId, Entry>>> tables = new AtomicReference<>(Map.of());
    private final Map<String, List<Entry>> sorted = new ConcurrentHashMap<>();
    private final AtomicLong refreshes = new AtomicLong();
    private volatile TaskScheduler scheduler;
    private volatile ScheduledTask periodic;
    private volatile Instant lastRebuild;
    private volatile Duration lastRebuildTook;
    private volatile String lastError;
    private volatile boolean closed;

    /**
     * @param banks        lecture d'une banque
     * @param content      lecture de toutes les banques (recalcul complet)
     * @param valuer       valeur d'un etat de banque pour une categorie d'items ({@code null} : toute la banque),
     *                     au catalogue de prix courant
     * @param enabledTypes classements actifs et nombre d'entrees conservees (rang maximal), lu a chaque usage
     *                     pour suivre le rechargement de la configuration. Cle = type d'entite ({@code player}) ou
     *                     type et categorie d'items ({@code player#blocs}, voir {@link #scope})
     * @param clock        horloge
     * @param logger       journal du plugin
     */
    public LeaderboardService(BankRepository banks, StorageContent content, BiFunction<Bank, String, BigDecimal> valuer,
                              Supplier<Map<String, Integer>> enabledTypes, Clock clock, Logger logger) {
        this.banks = Objects.requireNonNull(banks, "banks");
        this.content = Objects.requireNonNull(content, "content");
        this.valuer = Objects.requireNonNull(valuer, "valuer");
        this.enabledTypes = Objects.requireNonNull(enabledTypes, "enabledTypes");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Lance le premier calcul complet puis la relecture periodique.
     *
     * @param tasks  planificateur dont l'executeur asynchrone lit le stockage
     * @param period periode de recalcul complet, strictement positive
     */
    public void start(TaskScheduler tasks, Duration period) {
        this.scheduler = Objects.requireNonNull(tasks, "tasks");
        requestRebuild();
        periodic = tasks.runRepeatingAsync(this::rebuild, period, period);
    }

    /**
     * Demande un recalcul complet des que possible (fusionne s'il est deja demande).
     */
    public void requestRebuild() {
        TaskScheduler tasks = scheduler;
        if (tasks == null || closed || !rebuildQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            tasks.runAsync(() -> {
                rebuildQueued.set(false);
                rebuild();
            });
        } catch (RejectedExecutionException saturated) {
            rebuildQueued.set(false);
        }
    }

    /**
     * Relit toutes les banques et remplace les classements. Appele sur l'executeur IO.
     */
    public void rebuild() {
        if (closed) {
            return;
        }
        long start = System.nanoTime();
        try {
            Map<String, Integer> types = enabledTypes.get();
            Map<String, Map<BankId, Entry>> fresh = new HashMap<>();
            if (types.isEmpty()) {
                // Aucun classement actif : pas de relecture de toutes les banques.
                tables.set(fresh);
                sorted.clear();
                lastRebuild = clock.instant();
                lastRebuildTook = Duration.ZERO;
                lastError = null;
                return;
            }
            types.keySet().forEach(type -> fresh.put(type, new ConcurrentHashMap<>()));
            content.exportBanks(bank -> {
                for (String scope : types.keySet()) {
                    if (typeOf(scope).equals(bank.owner().type())) {
                        BigDecimal value = valuer.apply(bank, categoryOf(scope));
                        if (value != null && value.signum() > 0) {
                            fresh.get(scope).put(bank.id(), new Entry(bank, value));
                        }
                    }
                }
            });
            tables.set(fresh);
            sorted.clear();
            lastRebuildTook = Duration.ofNanos(System.nanoTime() - start);
            lastRebuild = clock.instant();
            lastError = null;
        } catch (RuntimeException failure) {
            lastError = failure.getClass().getSimpleName() + " : " + failure.getMessage();
            logger.log(Level.WARNING,
                    ConsoleText.tr("Classements : recalcul complet en echec, l'ancien classement reste en service"),
                            failure);
        }
    }

    /**
     * Recalcule une seule banque. Appele sur l'executeur IO.
     *
     * @param bankId banque
     */
    public void refresh(BankId bankId) {
        try {
            Optional<Bank> found = banks.findBank(bankId);
            Map<String, Map<BankId, Entry>> current = tables.get();
            if (found.isEmpty()) {
                current.values().forEach(table -> table.remove(bankId));
                sorted.clear();
                return;
            }
            Bank bank = found.get();
            for (Map.Entry<String, Map<BankId, Entry>> scope : current.entrySet()) {
                if (!typeOf(scope.getKey()).equals(bank.owner().type())) {
                    continue;
                }
                BigDecimal value = valuer.apply(bank, categoryOf(scope.getKey()));
                if (value != null && value.signum() > 0) {
                    scope.getValue().put(bankId, new Entry(bank, value));
                } else {
                    scope.getValue().remove(bankId);
                }
                sorted.remove(scope.getKey());
            }
            refreshes.incrementAndGet();
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, ConsoleText.tr("Classements : recalcul de la banque {0} en echec",
                    bankId), failure);
        }
    }

    @Override
    public void localChange(BankChange change) {
        schedule(change.bankId());
    }

    @Override
    public void onRemoteChange(BankChange change) {
        schedule(change.bankId());
    }

    @Override
    public void noteAnnounceFailure(RuntimeException failure) {
        logger.log(Level.WARNING, ConsoleText.tr("Classements : notification locale en echec"), failure);
    }

    private void schedule(BankId bankId) {
        TaskScheduler tasks = scheduler;
        if (tasks == null || closed || !pending.add(bankId)) {
            return;
        }
        try {
            tasks.runAsync(() -> {
                pending.remove(bankId);
                refresh(bankId);
            });
        } catch (RejectedExecutionException saturated) {
            // Le prochain recalcul complet rattrapera cette banque.
            pending.remove(bankId);
        }
    }

    /**
     * @param type     type d'entite
     * @param category categorie d'items, ou {@code null} pour le classement general
     * @return la cle d'un classement
     */
    public static String scope(String type, String category) {
        return category == null || category.isEmpty() ? type : type + "#" + category;
    }

    private static String typeOf(String scope) {
        int cut = scope.indexOf('#');
        return cut < 0 ? scope : scope.substring(0, cut);
    }

    private static String categoryOf(String scope) {
        int cut = scope.indexOf('#');
        return cut < 0 ? null : scope.substring(cut + 1);
    }

    /**
     * @param type  classement ({@code type} ou {@code type#categorie})
     * @param limit nombre maximal de lignes, positif
     * @return les meilleures banques du type, rang 1 en premier ; vide si le type n'est pas classe
     */
    public List<RankedEntry> top(String type, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("limit negatif : {0}", limit));
        }
        List<Entry> ranking = ranking(type);
        int cap = Math.min(limit, enabledTypes.get().getOrDefault(type, 0));
        List<RankedEntry> result = new ArrayList<>(Math.min(cap, ranking.size()));
        for (int i = 0; i < ranking.size() && i < cap; i++) {
            result.add(toRanked(i, ranking.get(i)));
        }
        return List.copyOf(result);
    }

    /**
     * @param type   type d'entite
     * @param bankId banque
     * @return son rang et sa valeur (sans limite de taille du classement), vide si non classee
     */
    public Optional<RankedEntry> rankOf(String type, BankId bankId) {
        List<Entry> ranking = ranking(type);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).bank.id().equals(bankId)) {
                return Optional.of(toRanked(i, ranking.get(i)));
            }
        }
        return Optional.empty();
    }

    /**
     * @return les compteurs du classement
     */
    public LeaderboardStats stats() {
        Map<String, Integer> sizes = new HashMap<>();
        tables.get().forEach((type, table) -> sizes.put(type, table.size()));
        return new LeaderboardStats(sizes, Optional.ofNullable(lastRebuild), Optional.ofNullable(lastRebuildTook),
                Optional.ofNullable(lastError), refreshes.get());
    }

    @Override
    public void close() {
        closed = true;
        ScheduledTask task = periodic;
        if (task != null) {
            task.cancel();
        }
    }

    private List<Entry> ranking(String type) {
        return sorted.computeIfAbsent(type, key -> {
            Map<BankId, Entry> table = tables.get().get(key);
            if (table == null) {
                return List.of();
            }
            List<Entry> list = new ArrayList<>(table.values());
            list.sort(ORDER);
            return List.copyOf(list);
        });
    }

    private static RankedEntry toRanked(int index, Entry entry) {
        return new RankedEntry(index + 1, entry.bank.id(), entry.bank.owner(), entry.value);
    }
}

package dev.novadev.novabank.config.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.model.AcceptedItem;
import dev.novadev.novabank.core.valuation.PriceCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ReloadablePriceCatalogTest {

    private static final List<ItemKey> KEYS = List.of(ItemKey.minecraft("stone"), ItemKey.minecraft("dirt"),
            ItemKey.parse("nexo:ruby"));
    private static final BigDecimal TWO = new BigDecimal("2");

    private static ConfigPriceCatalog uniformCatalog(String price) {
        List<AcceptedItem> items = new ArrayList<>();
        KEYS.forEach(key -> items.add(new AcceptedItem(key, new BigDecimal(price), Optional.empty(), Optional.empty())));
        return ConfigPriceCatalog.of(items);
    }

    @Test
    void delegue_au_catalogue_courant_et_rend_le_precedent() {
        ConfigPriceCatalog first = uniformCatalog("1");
        ConfigPriceCatalog second = uniformCatalog("2");
        ReloadablePriceCatalog reloadable = new ReloadablePriceCatalog(first);
        assertThat(reloadable.unitPrice(KEYS.get(0))).contains(new BigDecimal("1"));
        assertThat(reloadable.replace(second)).isSameAs(first);
        assertThat(reloadable.current()).isSameAs(second);
        assertThat(reloadable.unitPrice(KEYS.get(0))).contains(new BigDecimal("2"));
    }

    @Test
    void lectures_concurrentes_pendant_les_remplacements() throws Exception {
        ConfigPriceCatalog one = uniformCatalog("1");
        ConfigPriceCatalog two = uniformCatalog("2");
        ReloadablePriceCatalog reloadable = new ReloadablePriceCatalog(one);
        int readers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicLong reads = new AtomicLong();
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int r = 0; r < readers; r++) {
                tasks.add(pool.submit(() -> {
                    start.await();
                    while (writing.get()) {
                        Optional<BigDecimal> direct = reloadable.unitPrice(KEYS.get(0));
                        if (direct.isEmpty() || !(direct.get().equals(BigDecimal.ONE) || direct.get().equals(TWO))) {
                            failures.add("prix inattendu : " + direct);
                        }
                        PriceCatalog snapshot = reloadable.current();
                        BigDecimal reference = snapshot.unitPrice(KEYS.get(0)).orElseThrow();
                        for (ItemKey key : KEYS) {
                            if (!snapshot.unitPrice(key).orElseThrow().equals(reference)) {
                                failures.add("instantane incoherent pour " + key);
                            }
                        }
                        reads.incrementAndGet();
                    }
                    return null;
                }));
            }
            Future<?> writer = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 20_000; i++) {
                    reloadable.replace(i % 2 == 0 ? two : one);
                }
                writing.set(false);
                return null;
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            writing.set(false);
            pool.shutdownNow();
        }
        assertThat(failures).isEmpty();
        assertThat(reads.get()).isPositive();
        assertThat(reloadable.current()).isSameAs(one);
    }
}

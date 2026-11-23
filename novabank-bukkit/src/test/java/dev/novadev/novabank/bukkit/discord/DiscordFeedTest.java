package dev.novadev.novabank.bukkit.discord;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.event.BankOperationEvent;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.discord.DiscordTransport.Reply;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.config.model.DiscordSettings;
import dev.novadev.novabank.config.model.Secret;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Envoi vers Discord sans reseau : faux transport (reponses programmees) et horloge manuelle. */
class DiscordFeedTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final EntityRef ISLAND = new EntityRef("island", "pins");

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final List<String> sent = new ArrayList<>();
    private final Deque<CompletableFuture<Reply>> replies = new ArrayDeque<>();
    private final List<String> warnings = new ArrayList<>();
    private DiscordSettings settings = settings(true);
    private DiscordFeed feed;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord entry) {
                warnings.add(entry.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        feed = new DiscordFeed(() -> settings, () -> TestLanguages.bundled("en"),
                id -> Optional.of("Steve"), EntityRef::id, ItemKey::id, Runnable::run,
                (current, body) -> {
                    sent.add(body);
                    return replies.isEmpty() ? CompletableFuture.completedFuture(new Reply(204, "")) : replies.poll();
                }, now::get, logger);
    }

    @Test
    void les_depots_d_un_joueur_partent_en_une_ligne_dans_un_encadre_vert() {
        deposit("stone", 64);
        deposit("iron_block", 12);
        deposit("stone", 64);

        feed.flush();

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).contains("**Steve** deposited **128 x stone**, **12 x iron\\\\_block** into bank pins (+3)")
                .contains("\"color\":" + DiscordText.GREEN).doesNotContain("@everyone");
        assertThat(feed.pending()).isZero();
    }

    @Test
    void rien_ne_part_avant_le_delai_de_regroupement() {
        deposit("stone", 1);
        feed.flush();
        deposit("stone", 2);

        feed.flush();
        assertThat(sent).hasSize(1);

        now.addAndGet(settings.batchSeconds() * 1000L);
        feed.flush();
        assertThat(sent).hasSize(2);
    }

    @Test
    void une_limite_de_debit_repousse_l_envoi_du_delai_demande_sans_rien_perdre() {
        replies.add(CompletableFuture.completedFuture(new Reply(429, "{\"retry_after\": 2.5}")));
        deposit("stone", 1);

        feed.flush();
        now.addAndGet(2_000);
        feed.flush();
        assertThat(sent).hasSize(1);

        now.addAndGet(600);
        feed.flush();
        assertThat(sent).hasSize(2).allSatisfy(body -> assertThat(body).contains("**1 x stone**"));
    }

    @Test
    void un_salon_injoignable_garde_les_messages_dans_l_ordre_et_le_signale_une_fois() {
        replies.add(CompletableFuture.failedFuture(new java.io.IOException("connexion refusee")));
        replies.add(CompletableFuture.failedFuture(new java.io.IOException("connexion refusee")));
        for (int i = 0; i < 12; i++) {
            deposit(new EntityRef("island", "i" + i), "stone", i + 1);
        }

        feed.flush();
        now.addAndGet(settings.batchSeconds() * 1000L);
        feed.flush();
        now.addAndGet(settings.batchSeconds() * 1000L);
        feed.flush();

        // 12 encadres = 2 messages : 2 echecs du premier, puis le premier et le second dans l'ordre.
        assertThat(sent).hasSize(4);
        assertThat(sent.get(2)).isEqualTo(sent.get(0)).contains("**1 x stone**");
        assertThat(sent.get(3)).contains("**12 x stone**");
        assertThat(warnings).hasSize(1);
    }

    @Test
    void un_refus_definitif_abandonne_le_lot_et_le_signale() {
        replies.add(CompletableFuture.completedFuture(new Reply(403, "{}")));
        deposit("stone", 1);

        feed.flush();
        now.addAndGet(settings.batchSeconds() * 1000L);
        feed.flush();

        assertThat(sent).hasSize(1);
        assertThat(warnings).singleElement().asString().contains("403");
    }

    @Test
    void la_file_garde_au_plus_500_operations() {
        for (int i = 0; i < DiscordFeed.MAX_QUEUED + 100; i++) {
            deposit("stone", 1);
        }

        assertThat(feed.pending()).isEqualTo(DiscordFeed.MAX_QUEUED);
    }

    @Test
    void salon_desactive_rien_n_est_envoye() {
        settings = settings(false);
        deposit("stone", 1);
        feed.flush();
        List<String> chat = new ArrayList<>();

        feed.test(chat::add);

        assertThat(sent).isEmpty();
        assertThat(chat).singleElement().asString().contains("off");
    }

    @Test
    void l_essai_affiche_la_reponse_de_discord() {
        List<String> chat = new ArrayList<>();
        feed.test(chat::add);
        assertThat(chat).hasSize(2);
        assertThat(chat.get(1)).contains("received");
        assertThat(sent.get(0)).contains("\"color\":" + DiscordText.BLUE);

        replies.add(CompletableFuture.completedFuture(new Reply(404, "{}")));
        chat.clear();
        feed.test(chat::add);
        assertThat(chat.get(1)).contains("404").contains("webhook deleted");

        replies.add(CompletableFuture.failedFuture(new java.io.IOException("pas de reseau")));
        chat.clear();
        feed.test(chat::add);
        assertThat(chat.get(1)).contains("unreachable").contains("pas de reseau");
    }

    private void deposit(String item, long quantity) {
        deposit(ISLAND, item, quantity);
    }

    private void deposit(EntityRef owner, String item, long quantity) {
        feed.onOperation(new BankOperationEvent(OperationId.random(), BankId.forOwner(owner), owner,
                OperationType.DEPOSIT, "COMMITTED", ItemKey.minecraft(item), quantity, BigDecimal.ONE, 1,
                STEVE));
    }

    private static DiscordSettings settings(boolean enabled) {
        return new DiscordSettings(enabled, DiscordSettings.Mode.WEBHOOK, Secret.of("u"), Secret.of(""), "",
                Set.of(OperationType.DEPOSIT, OperationType.WITHDRAW), List.of(), 1, 5);
    }
}

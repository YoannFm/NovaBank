package dev.novadev.novabank.bukkit.discord;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.event.BankOperationEvent;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.discord.DiscordText.Announce;
import dev.novadev.novabank.bukkit.discord.DiscordText.Embed;
import dev.novadev.novabank.bukkit.discord.DiscordText.Group;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.config.model.DiscordSettings;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * Annonce les operations dans un salon Discord : {@code Steve a depose 64 x stone, 12 x iron_block dans la banque Ile des
 * Pins}. Les operations enregistrees sur ce serveur sont mises en file, puis envoyees au plus toutes les
 * {@code discord.batch-seconds} secondes, par webhook ou par bot : les operations d'un meme joueur sur une meme banque
 * forment une seule ligne, dans un encadre vert (depot), rouge (retrait) ou orange (reset). Une limite de debit de
 * Discord (HTTP 429) repousse l'envoi du delai demande ; un refus (jeton, adresse, droits) est signale une fois en
 * console. Les reglages sont relus a chaque operation : un rechargement s'applique sans redemarrage.
 */
public final class DiscordFeed implements Listener, AutoCloseable {

    /** Operations en attente au plus (salon injoignable longtemps) : les plus anciennes sont abandonnees. */
    static final int MAX_QUEUED = 500;

    private final Supplier<DiscordSettings> settings;
    private final Supplier<Language> language;
    private final Function<UUID, Optional<String>> playerNames;
    private final Function<EntityRef, String> bankNames;
    private final Function<ItemKey, String> itemNames;
    private final Consumer<Runnable> mainThread;
    private final DiscordTransport sender;
    private final LongSupplier clock;
    private final Logger logger;
    private final Deque<Announce> queue = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean sending = new AtomicBoolean();
    /** Messages deja construits pas encore envoyes (limite de debit, salon injoignable) ; lu sous {@link #sending}. */
    private volatile List<String> unsent = List.of();
    private volatile long nextSendMillis;
    private volatile String lastProblem = "";

    /**
     * @param settings    section {@code discord} en vigueur
     * @param language    langue en vigueur (textes {@code discord.*})
     * @param playerNames nom connu d'un joueur
     * @param bankNames   nom lisible d'une banque (nom du joueur, de l'ile, de la ville...)
     * @param itemNames   nom affiche d'un item
     * @param mainThread  execute une tache sur le thread qui peut lire les plugins hotes
     * @param version     version de NovaBank (en-tete HTTP)
     * @param logger      journal
     */
    public DiscordFeed(Supplier<DiscordSettings> settings, Supplier<Language> language,
                       Function<UUID, Optional<String>> playerNames, Function<EntityRef, String> bankNames,
                       Function<ItemKey, String> itemNames, Consumer<Runnable> mainThread, String version, Logger logger) {
        this(settings, language, playerNames, bankNames, itemNames, mainThread, new DiscordSender(version),
                System::currentTimeMillis, logger);
    }

    /** Envoi et horloge fournis (tests). */
    DiscordFeed(Supplier<DiscordSettings> settings, Supplier<Language> language,
                Function<UUID, Optional<String>> playerNames, Function<EntityRef, String> bankNames,
                Function<ItemKey, String> itemNames, Consumer<Runnable> mainThread, DiscordTransport sender,
                LongSupplier clock, Logger logger) {
        this.settings = settings;
        this.language = language;
        this.playerNames = playerNames;
        this.bankNames = bankNames;
        this.itemNames = itemNames;
        this.mainThread = mainThread;
        this.sender = sender;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * @param event operation enregistree sur ce serveur (thread d'entrees/sorties)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOperation(BankOperationEvent event) {
        DiscordSettings now = settings.get();
        if (announced(now, event)) {
            mainThread.accept(() -> enqueue(announce(event)));
        }
    }

    /** Depot ou reset enregistres, retrait solde : une seule ligne par operation. */
    static boolean announced(DiscordSettings settings, BankOperationEvent event) {
        if (!settings.enabled() || !settings.events().contains(event.type()) || !"COMMITTED".equals(event.state())) {
            return false;
        }
        if (!settings.bankTypes().isEmpty() && !settings.bankTypes().contains(event.owner().type())) {
            return false;
        }
        return event.type() == OperationType.RESET || event.quantity() >= settings.minQuantity();
    }

    /** Noms lus sur le thread principal (plugins hotes), echappes pour Discord. */
    private Announce announce(BankOperationEvent event) {
        String player = event.actor().flatMap(playerNames).map(DiscordText::escape)
                .orElseGet(() -> plain(language.get().text("discord.unknown-player")));
        String item = event.type() == OperationType.RESET ? ""
                : event.item().map(itemNames).map(DiscordText::escape).orElse("");
        return new Announce(event.type(), player, DiscordText.escape(bankNames.apply(event.owner())), item,
                event.quantity(), event.valueDelta().abs());
    }

    private Embed embed(Language text, Group group) {
        String items = group.items().entrySet().stream()
                .map(entry -> text.text("discord.item", "quantity", text.quantity(entry.getValue()), "item", entry.getKey()))
                .collect(Collectors.joining(", "));
        String key = switch (group.type()) {
            case DEPOSIT -> "discord.deposit";
            case WITHDRAW -> "discord.withdraw";
            default -> "discord.reset";
        };
        String line = text.text(key, "player", group.player(), "items", items, "bank", group.bank(),
                "value", group.value().stripTrailingZeros().toPlainString());
        return new Embed(plain(line), Embed.color(group.type()));
    }

    private static String plain(String colored) {
        return ChatColor.stripColor(colored);
    }

    private void enqueue(Announce announce) {
        queue.addLast(announce);
        while (queue.size() > MAX_QUEUED) {
            queue.pollFirst();
        }
    }

    /** @return operations en attente d'envoi (tests) */
    int pending() {
        return queue.size();
    }

    /** Envoie ce qui attend, si le delai de regroupement et les limites de Discord le permettent (appel periodique). */
    public void flush() {
        DiscordSettings now = settings.get();
        long time = clock.getAsLong();
        if (!now.enabled() || time < nextSendMillis || !sending.compareAndSet(false, true)) {
            return;
        }
        if (unsent.isEmpty()) {
            List<Announce> announces = new ArrayList<>();
            for (Announce announce = queue.pollFirst(); announce != null; announce = queue.pollFirst()) {
                announces.add(announce);
            }
            Language text = language.get();
            unsent = DiscordText.payloads(DiscordText.group(announces).stream().map(group -> embed(text, group)).toList());
        }
        if (unsent.isEmpty()) {
            sending.set(false);
            return;
        }
        nextSendMillis = time + now.batchSeconds() * 1000L;
        send(now, 0);
    }

    private void send(DiscordSettings now, int index) {
        if (index >= unsent.size()) {
            done(List.of());
            return;
        }
        sender.send(now, unsent.get(index)).whenComplete((response, failure) -> {
            if (failure != null) {
                problem(ConsoleText.tr("Discord : salon injoignable ({0}), nouvel essai plus tard",
                        failure.getMessage()));
                done(unsent.subList(index, unsent.size()));
            } else if (response.status() == 429) {
                nextSendMillis = clock.getAsLong() + (long) (DiscordText.retryAfter(response.body()) * 1000);
                done(unsent.subList(index, unsent.size()));
            } else if (response.status() >= 300) {
                problem(ConsoleText.tr("Discord : envoi refuse (HTTP {0}) : verifiez discord.mode, discord.webhook-url ou discord.bot-token et discord.channel-id, et les droits du bot dans le salon",
                        response.status()));
                done(List.of());
            } else {
                lastProblem = "";
                send(now, index + 1);
            }
        });
    }

    /** @param remaining messages a renvoyer au prochain passage, dans l'ordre */
    private void done(List<String> remaining) {
        unsent = List.copyOf(remaining);
        sending.set(false);
    }

    private void problem(String message) {
        if (!message.equals(lastProblem)) {
            lastProblem = message;
            logger.warning(message);
        }
    }

    /**
     * {@code /novabank discord test} : envoie tout de suite un message d'essai, sans passer par la file.
     *
     * @param reply recoit le compte rendu (texte de chat), sur le thread principal
     */
    public void test(Consumer<String> reply) {
        DiscordSettings now = settings.get();
        Language text = language.get();
        if (!now.enabled()) {
            reply.accept(text.chat("admin.discord.disabled"));
            return;
        }
        reply.accept(text.chat("admin.discord.sending"));
        String body = DiscordText.json(List.of(new Embed(plain(text.text("discord.test")), DiscordText.BLUE)));
        sender.send(now, body).whenComplete((response, failure) -> {
            String result;
            if (failure != null) {
                result = text.chat("admin.discord.unreachable", "error", String.valueOf(failure.getMessage()));
            } else if (response.status() < 300) {
                result = text.chat("admin.discord.ok");
            } else {
                result = text.chat("admin.discord.refused", "status", response.status(),
                        "hint", text.text("admin.discord.hint." + hint(response.status())));
            }
            mainThread.accept(() -> reply.accept(result));
        });
    }

    /** @param status statut HTTP d'un refus @return suffixe de la cle d'aide {@code admin.discord.hint.*} */
    static String hint(int status) {
        return switch (status) {
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 429 -> "rate-limited";
            default -> "other";
        };
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }
}

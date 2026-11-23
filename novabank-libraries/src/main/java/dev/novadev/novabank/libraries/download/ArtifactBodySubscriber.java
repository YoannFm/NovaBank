package dev.novadev.novabank.libraries.download;

import dev.novadev.novabank.libraries.artifact.ArtifactChecksums;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Recoit le corps d'une reponse HTTP dans un fichier, en calculant sa somme SHA-256 et en
 * l'interrompant des que la taille epinglee est depassee. Memorise l'instant de la derniere
 * donnee recue pour detecter un transfert bloque.
 *
 * <p>Un objet par essai. Le fichier est ouvert et ferme par l'appelant.
 */
final class ArtifactBodySubscriber implements HttpResponse.BodySubscriber<Void> {

    private final LibraryArtifact artifact;
    private final FileChannel channel;
    private final MessageDigest digest = ArtifactChecksums.newDigest();
    private final CompletableFuture<Void> body = new CompletableFuture<>();
    private final AtomicReference<AttemptFailure> failure = new AtomicReference<>();
    private volatile Flow.Subscription subscription;
    private volatile long lastActivityNanos = System.nanoTime();
    /** Ecrit uniquement par onNext (appels serialises), lu apres la fin du corps. */
    private long received;

    ArtifactBodySubscriber(LibraryArtifact artifact, FileChannel channel) {
        this.artifact = artifact;
        this.channel = channel;
    }

    /**
     * Gestionnaire de corps : refuse d'emblee un statut autre que 200 ou une taille
     * annoncee superieure a la taille epinglee, sans lire le corps.
     */
    HttpResponse.BodySubscriber<Void> accept(HttpResponse.ResponseInfo info) {
        touch();
        int status = info.statusCode();
        if (status != 200) {
            fail(new AttemptFailure("HTTP " + status, status >= 500 || status == 408 || status == 429));
        } else {
            long declared = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declared > artifact.size()) {
                fail(tooLarge(declared));
            }
        }
        return this;
    }

    @Override
    public void onSubscribe(Flow.Subscription newSubscription) {
        subscription = newSubscription;
        if (failure.get() != null) {
            newSubscription.cancel();
        } else {
            newSubscription.request(1);
        }
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) {
            return;
        }
        touch();
        try {
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (received + length > artifact.size()) {
                    fail(tooLarge(received + length));
                    return;
                }
                digest.update(buffer.duplicate());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                received += length;
            }
        } catch (IOException writeFailure) {
            fail(new AttemptFailure("ecriture du fichier temporaire impossible : " + writeFailure, false));
            return;
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
        body.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
        body.complete(null);
    }

    @Override
    public CompletionStage<Void> getBody() {
        return body;
    }

    /**
     * Interrompt le transfert avec la cause donnee (la premiere cause est conservee).
     */
    void fail(AttemptFailure cause) {
        if (failure.compareAndSet(null, cause)) {
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
            body.completeExceptionally(cause);
        }
    }

    /**
     * @return la cause d'interruption decidee par ce recepteur, s'il y en a une
     */
    Optional<AttemptFailure> failure() {
        return Optional.ofNullable(failure.get());
    }

    /**
     * @return le temps ecoule depuis la derniere activite (envoi, en-tetes ou donnees), en nanosecondes
     */
    long idleNanos() {
        return System.nanoTime() - lastActivityNanos;
    }

    /**
     * Controle le corps recu une fois le transfert termine : taille exacte et somme SHA-256.
     *
     * @throws AttemptFailure si le contenu n'est pas l'artefact epingle
     */
    void verifyComplete() throws AttemptFailure {
        if (received != artifact.size()) {
            throw new AttemptFailure("transfert incomplet (" + received + " octets recus sur "
                    + artifact.size() + ")", true);
        }
        byte[] actual = digest.digest();
        if (!ArtifactChecksums.matches(artifact, actual)) {
            throw new AttemptFailure("somme SHA-256 invalide (recue " + ArtifactChecksums.hex(actual)
                    + ", attendue " + artifact.sha256() + ")", false);
        }
    }

    private AttemptFailure tooLarge(long length) {
        return new AttemptFailure("fichier trop gros (" + length + " octets ou plus, attendu "
                + artifact.size() + ")", false);
    }

    private void touch() {
        lastActivityNanos = System.nanoTime();
    }
}

/**
 * Stockage fichier YAML / JSON, mono-serveur (decision D4).
 *
 * <h2>Principe</h2>
 * Tout l'etat (banques, journal d'operations, livraisons, banques actives) est en memoire, protege
 * par un verrou lecture/ecriture. Chaque mutation acceptee est d'abord ajoutee au JOURNAL D'ECRITURE
 * ({@code novabank.<ext>.journal}, une ligne par mutation, voir {@link
 * dev.novadev.novabank.storage.file.JournalFile}) et synchronisee sur le disque
 * ({@code FileChannel.force(true)}) AVANT d'etre appliquee en memoire et confirmee a l'appelant.
 * L'INSTANTANE ({@code novabank.json} ou {@code novabank.yml}, seule partie dont le format depend
 * du type choisi, voir {@link dev.novadev.novabank.storage.file.SnapshotCodec}) est reecrit
 * (fichier temporaire synchronise puis renommage atomique) quand le journal depasse une taille ou
 * une anciennete, et a l'arret propre ; le journal est alors vide.
 *
 * <p>Au demarrage : instantane, puis rejeu des lignes du journal posterieures a sa sequence. Une
 * derniere ligne tronquee (arret brutal pendant l'ecriture) est ignoree, conservee a part et
 * signalee ; une ligne invalide suivie d'autres lignes est une corruption : ouverture refusee. Un
 * verrou de fichier exclusif ({@code novabank.<ext>.lock}) interdit l'ouverture du meme stockage
 * par un second processus (second serveur).
 *
 * <p>Gson et SnakeYAML sont fournis par le serveur : seule l'API commune aux versions embarquees est
 * utilisee (compilation contre SnakeYAML 1.28 et Gson 2.8.0, celles de l'API Spigot 1.17.1 ;
 * constate au demarrage : Paper 1.17.1 SnakeYAML 1.28 / Gson 2.8.8, Paper 1.21.11 SnakeYAML 2.2 /
 * Gson 2.13.2, Paper 26.3 SnakeYAML 2.2 / Gson 2.14.0).
 */
package dev.novadev.novabank.storage.file;

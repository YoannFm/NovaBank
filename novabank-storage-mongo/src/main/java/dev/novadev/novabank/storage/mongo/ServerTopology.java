package dev.novadev.novabank.storage.mongo;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * Nature du serveur, lue par les commandes {@code hello} et {@code buildInfo} a l'ouverture.
 *
 * <p>Les transactions multi-documents exigent un replica set (ou un cluster shard, routeur
 * {@code mongos}) et des sessions logiques ; un serveur autonome ne les supporte pas.
 *
 * @param version    version du serveur
 * @param replicaSet nom du replica set, vide pour un serveur autonome ou un routeur
 * @param sharded    {@code true} si le serveur est un routeur {@code mongos}
 * @param sessions   {@code true} si le serveur annonce des sessions logiques
 */
record ServerTopology(String version, String replicaSet, boolean sharded, boolean sessions) {

    /**
     * @param admin base {@code admin}
     * @return la nature du serveur
     */
    static ServerTopology detect(MongoDatabase admin) {
        Document hello = admin.runCommand(new Document("hello", 1));
        Document buildInfo = admin.runCommand(new Document("buildInfo", 1));
        String setName = hello.getString("setName");
        return new ServerTopology(String.valueOf(buildInfo.getString("version")), setName == null ? "" : setName,
                "isdbgrid".equals(hello.getString("msg")), hello.get("logicalSessionTimeoutMinutes") != null);
    }

    /**
     * @return {@code true} si les transactions multi-documents sont disponibles
     */
    boolean supportsTransactions() {
        return sessions && (sharded || !replicaSet.isEmpty());
    }

    /**
     * Garantie d'ecriture durable selon la nature du serveur : sur un serveur autonome,
     * {@code w: 1, j: true} n'acquitte qu'apres ecriture du journal sur disque ({@code majority}
     * n'y apporte rien) ; sur replica set ou cluster, {@code w: "majority"} journalise. Verifie
     * par {@code MongoOutageTest} ({@code kill -9} du serveur : toute operation annoncee reussie
     * est retrouvee apres redemarrage).
     *
     * @return {@code w: majority, j: true} si replique, {@code w: 1, j: true} sinon
     */
    WriteConcern durableWriteConcern() {
        return sharded || !replicaSet.isEmpty() ? WriteConcern.MAJORITY.withJournal(true) : WriteConcern.JOURNALED;
    }

    /**
     * @return description pour le journal du serveur
     */
    String describe() {
        String kind = sharded ? "cluster shard" : replicaSet.isEmpty() ? "serveur autonome" : "replica set " + replicaSet;
        String mode = supportsTransactions() ? "transactions multi-documents"
                : "sans transaction (journal en attente par banque)";
        return "MongoDB " + version + ", " + kind + " : " + mode;
    }
}

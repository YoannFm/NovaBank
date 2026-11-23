package dev.novadev.novabank.storage.mongo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Test sur un vrai serveur MongoDB, ignore sans serveur. Lancement :
 * {@code scripts/db/with-db.sh mongodb|mongodb-rs mvn -B -o test -pl novabank-storage-mongo -Dgroups=mongodb-server}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(MongoTestServer.TAG)
@EnabledIf("dev.novadev.novabank.storage.mongo.MongoTestServer#available")
@interface RequiresMongoServer {
}

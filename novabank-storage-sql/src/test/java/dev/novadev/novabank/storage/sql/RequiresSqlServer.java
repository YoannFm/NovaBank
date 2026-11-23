package dev.novadev.novabank.storage.sql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Test sur un vrai serveur MySQL ou MariaDB : tag {@value ServerDatabase#TAG}, execute seulement
 * si un serveur est configure (voir {@link ServerDatabase}), ignore sinon pour que le build
 * reste vert sans base. Lancement : {@code scripts/db/with-db.sh <mariadb|mysql> mvn -B -o test
 * -pl novabank-storage-sql -Dgroups=sql-server}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(ServerDatabase.TAG)
@EnabledIf("dev.novadev.novabank.storage.sql.ServerDatabase#available")
@interface RequiresSqlServer {
}

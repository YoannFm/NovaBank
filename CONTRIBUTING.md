# Contributing to NovaBank

Thanks for your interest! Bug reports, ideas and pull requests are welcome.

## Reporting a bug

Open an issue with:

- the full output of `/novabank diagnostic`;
- the console lines starting with `[NovaBank]` around the problem (with `diagnostics.debug: true` if possible);
- the server software and version (Paper, Spigot, Folia), the Java version, the NovaBank JAR you installed;
- your `config.yml` **without** passwords, tokens, webhook URLs or connection URIs.

## Building and testing

Requirements: JDK 25 and Maven 3.9 or later.

```bash
mvn -B install
```

This runs every unit test. Tests that need a real database server are skipped unless one is configured, through
environment variables (or `-Dnovabank.it.db.<key>=...` system properties):

| Variable | Used by | Value |
|---|---|---|
| `NOVABANK_IT_DB_PRODUCT` | all | `mariadb`, `mysql`, `mongodb`, `mongodb-rs` (replica set) or `redis` |
| `NOVABANK_IT_DB_HOST`, `NOVABANK_IT_DB_PORT` | SQL, Redis | server address |
| `NOVABANK_IT_DB_NAME`, `NOVABANK_IT_DB_USER`, `NOVABANK_IT_DB_PASSWORD` | SQL | an account allowed to create databases |
| `NOVABANK_IT_DB_URI` | MongoDB | connection URI |
| `NOVABANK_IT_DB_CTL` | outage tests (optional) | script that stops and restarts the server: `bash <script> <product> kill\|stop\|start` |

Example, MariaDB on the local machine:

```bash
NOVABANK_IT_DB_PRODUCT=mariadb NOVABANK_IT_DB_HOST=127.0.0.1 NOVABANK_IT_DB_PORT=3306 \
NOVABANK_IT_DB_NAME=mysql NOVABANK_IT_DB_USER=root NOVABANK_IT_DB_PASSWORD=secret \
mvn -B test -pl novabank-storage-sql
```

The GitHub Actions workflow (`.github/workflows/build.yml`) runs the same suites against real MariaDB, MySQL,
MongoDB and Redis services.

## Pull requests

- One topic per pull request, with tests for any new logic.
- `mvn -B install` must pass.
- Keep the architecture: `novabank-core` never depends on Bukkit; `novabank-api` depends on nothing else from
  NovaBank (ArchUnit tests enforce it).
- Never break the data already stored by servers: bank ids (`BankId.forOwner`), item keys (`namespace:id`) and
  storage schemas are a contract, pinned by `PersistedFormatsTest`. Schema changes go through a migration.
- Player-facing texts go into both `lang/fr.yml` and `lang/en.yml`; console texts go through `ConsoleText.tr` with
  their English translation in `novabank-core/src/main/resources/console/en.tsv` (checked by `ConsoleTextCatalogTest`).
- The build targets Java 17 bytecode and the Bukkit 1.17 API: do not use newer Bukkit methods without a fallback.

The existing comments and test names are in French; new code may be written in English.

## Licence

By contributing, you agree that your contribution is licensed under the [MIT licence](LICENSE).

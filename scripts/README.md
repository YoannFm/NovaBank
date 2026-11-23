# Test scripts

Tools used for the real-server test campaign. They are **not needed to build** NovaBank (`mvn -B install` is enough).

| Folder | Content |
|---|---|
| `db/` | start, stop and kill local MariaDB, MySQL, MongoDB and Redis servers for the integration tests (`with-db.sh`); written for macOS with Homebrew |
| `servers/` | download Paper, Folia or Spigot, start a test server with NovaBank, run the smoke test, the bot scenarios, the two-server sync check and the version matrix |
| `bot/` | Mineflayer bot that plays the player scenarios (deposit, withdrawal, menu, crash, restart) |
| `cartographie.py` | regenerates `.breakdown/` (module map, Discord flow) |

The `servers/` and `bot/` scripts read `$NOVABANK_TOOLS_DIR/env.sh` (default `~/novabank-tools/env.sh`), which exports
`JAVA_HOME`, `JAVA17_HOME`, `JAVA21_HOME`, `JAVA25_HOME` and the Node.js path; see `servers/common.sh`.

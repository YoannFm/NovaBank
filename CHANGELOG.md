# Changelog

All notable changes to NovaBank. Full details: the *Changelog* chapter of the documentation (PDF attached to each release).

Numbering follows the NovaDev format: `YYYY.MM.DD (MAJOR.MINOR.FEATURE)`.

## 2026.11.23 (1.0.0 #1) - first release

NovaBank is developed in one go; this version gathers everything below. MIT licence.

**Extensions per host plugin**

- One JAR per host plugin: SuperiorSkyblock2 module, BentoBox addon, IridiumSkyblock, Towny, SimpleClans, Lands, GriefPrevention and WorldGuard companion plugins; the standalone plugin stays available.
- Bank command inside the host (`/is bank`, `/town bank`, `/clan coffre`...) or its own command (`/landbank`, `/claimbank`, `/regionbank`); name and aliases configurable (`commands.hosts`, settings menu).
- Deposit and withdrawal rights set in the host: SuperiorSkyblock2 island privileges, BentoBox and Lands flags, IridiumSkyblock island permissions, Towny and SimpleClans rank permissions, WorldGuard region flags. API: `OwnershipProvider.allows`.
- BentoBox island level: the bank value is added to the level computed by the Level addon (`integrations.bentobox`).
- `/is bank` help in the server language; SuperiorSkyblock2 permission menu entries written to `plugins/NovaBank/hosts/`.
- Several islands, lands or claims: the host's command takes the entity where the player stands (BentoBox: the command's game mode, `/ob bank`), otherwise their previous choice.
- SimpleClans: a clan's bank follows its tag change.
- SuperiorSkyblock2: changing `apply-to` removes the old bonus; `/novabank resync` recomputes the level of every island.
- JAR file names without spaces or `#`.
- Several extensions on one server: a single engine, a single configuration and a single database (`plugins/NovaBank/`).

**Discord channel and console**

- Deposits, withdrawals and resets announced in a Discord channel, by webhook or bot; coloured embeds, operations of one player grouped on one line, Discord limits respected.
- `/novabank discord test`: test message and Discord's answer in the chat.
- Console in French or English according to `language`, technical causes included.
- `config.yml` commented in English: the comments follow `language` on reload (values unchanged).
- API: `BankOperationEvent.actor()`, the player who started the operation.
- SimpleClans: no automatic merge if the bank of the new tag already has stock.

**Core and storage**

- Banks per entity, idempotent operation log, anti-duplication (deposit, withdrawal reserved then settled, delayed delivery or return to the bank).
- `DERIVED` and `ACCUMULATED` valuation models, reset by baseline and `resetEpoch`.
- Six storages: YAML, JSON, SQLite, MySQL, MariaDB, MongoDB; in-game migration between them; drivers downloaded with SHA-256.
- Multi-server synchronisation: change feed in the database, Redis as an accelerator (built-in RESP client).

**Gameplay**

- `/bank` (players) and `/novabank` (administration) commands, chest menu, leaderboards (in-memory cache, per category).
- Integrations: 8 entity plugins (islands, towns, clans, lands, claims and regions; others through the API) and 5 custom item plugins.
- `NovaBankApi` public API and asynchronous events.

**In-game configuration**

- 4-row `/bank admin` menu: items and prices, bank menu layout, rows, save, discard, send to other servers, reload; CANCEL / CONFIRM confirmation; Escape goes back to the menu.
- Bank menu with free button placement (`gui.buttons.<id>.slot`).
- Safe writing: backup to `backup/` before each change, full validation, atomic replacement.
- Redis deployment of the shared sections (`redis.deploy-config`), catch-up `config push`.
- Click an inventory item to add it to the list; item sheet: price (-50, -0.50, +0.50, +50, chat input), category and display name typed in the chat, deletion; `/novabank items name` command.
- Confirmation screen: red panes on the left to cancel, green on the right to confirm.

**Messages and help**

- Blue, yellow, green, red palette; `NovaBank »` prefix.
- `/novabank` home screen (author, version, description, Discord, GitHub) and clickable help, open to everyone without granting any right.

**Island level and placeholders**

- SuperiorSkyblock2 island level (`integrations.superiorskyblock2`) and BentoBox (Level addon, `integrations.bentobox`).
- PlaceholderAPI placeholders (`%novabank_value%`, `%novabank_top_...%`).

> Configuration (`config-version`) and language (`lang-version`) file versions are managed automatically: new keys are added without touching your values.

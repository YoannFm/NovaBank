NovaBank @VERSION@
Free and open source (MIT) - https://github.com/YoannFm/NovaBank

=== ENGLISH ===

Install ONE file per server, depending on the plugins you use:

  No territory plugin, or several of them
      Standalone/NovaBank-@FILE_VERSION@.jar               -> plugins/

  SuperiorSkyblock2
      Extensions/SuperiorSkyblock2/...jar                  -> plugins/SuperiorSkyblock2/modules/
  BentoBox
      Extensions/BentoBox/...jar                           -> plugins/BentoBox/addons/
  IridiumSkyblock, Towny, SimpleClans, Lands, GriefPrevention or WorldGuard
      Extensions/<Plugin>/...jar                           -> plugins/

  Several host plugins on the same server: install each extension you need; they share one engine,
  one configuration and one database.

Then restart the server (not /reload). Configuration: plugins/NovaBank/config.yml
(SuperiorSkyblock2 module: plugins/SuperiorSkyblock2/modules/NovaBank/config.yml,
BentoBox addon: plugins/BentoBox/addons/NovaBank/config.yml), or /bank admin in game.

  API/novabank-api-@FILE_VERSION@.jar   for plugin developers only, never install it on a server.
  NovaBank - Documentation complete.pdf full documentation (English and French).

Support: https://github.com/YoannFm/NovaBank/issues (attach the output of /novabank diagnostic)
         https://discord.gg/HtjPAfqUXu

=== FRANCAIS ===

Installez UN seul fichier par serveur, selon les plugins que vous utilisez :

  Aucun plugin de territoire, ou plusieurs
      Standalone/NovaBank-@FILE_VERSION@.jar               -> plugins/

  SuperiorSkyblock2
      Extensions/SuperiorSkyblock2/...jar                  -> plugins/SuperiorSkyblock2/modules/
  BentoBox
      Extensions/BentoBox/...jar                           -> plugins/BentoBox/addons/
  IridiumSkyblock, Towny, SimpleClans, Lands, GriefPrevention ou WorldGuard
      Extensions/<Plugin>/...jar                           -> plugins/

  Plusieurs plugins hotes sur le meme serveur : installez chaque extension utile ; elles partagent un seul
  moteur, une seule configuration et une seule base.

Puis redemarrez le serveur (pas de /reload). Configuration : plugins/NovaBank/config.yml
(module SuperiorSkyblock2 : plugins/SuperiorSkyblock2/modules/NovaBank/config.yml,
addon BentoBox : plugins/BentoBox/addons/NovaBank/config.yml), ou /bank admin en jeu.

  API/novabank-api-@FILE_VERSION@.jar   reserve aux developpeurs de plugins, a ne jamais installer sur un serveur.
  NovaBank - Documentation complete.pdf documentation complete (francais et anglais).

Support : https://github.com/YoannFm/NovaBank/issues (joindre la sortie de /novabank diagnostic)
          https://discord.gg/HtjPAfqUXu

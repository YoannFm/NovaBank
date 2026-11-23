'use strict'
// Scenarios reels NovaBank, par phase (un demarrage de serveur par phase) :
//   main    : politique PENDING_DELIVERY, base neuve : a b c d f g j e i, puis h (depot + stop immediat)
//   restart : verification de h apres redemarrage (total conserve, sans duplication)
//   return  : politique RETURN_TO_BANK : e (inventaire plein), i (deconnexion), puis h2 (depot +
//             stop 100 ms apres : le depot est en general deja engage), verifie par un second restart
// Usage : node scenarios.js <phase>. Prix de test : stone 0.5, dirt 0.1, cobblestone 0.1 ; sand refuse.
const { config, sleep, consoleCommand, logSize, waitLog, TestBot, readState, writeState, check, runScenarios } =
  require('./harness')
const fs = require('fs')
const path = require('path')

const NAME = config.username
const STONE = 'minecraft:stone'
const PAUSE = 700 // > delai entre deux operations (500 ms)
const GUI_ICONS = ['book', 'arrow', 'hopper', 'barrier', 'gray_stained_glass_pane']

// Pierre renommee : composants depuis 1.20.5, NBT avant.
function renamedStone () {
  const [major, minor, patch] = config.version.split('.').map(Number)
  const components = major > 1 || minor > 20 || (minor === 20 && (patch || 0) >= 5)
  // custom_name : chaine JSON de 1.20.5 a 1.21.4, SNBT depuis 1.21.5.
  const snbt = major > 1 || minor > 21 || (minor === 21 && (patch || 0) >= 5)
  return components ? (snbt ? 'minecraft:stone[custom_name="Pierre speciale"]'
    : 'minecraft:stone[custom_name=\'"Pierre speciale"\']')
    : 'minecraft:stone{display:{Name:\'"Pierre speciale"\'}}'
}

// Donne des items par la console et attend leur arrivee (total attendu de ce type).
async function give (bot, item, count, expectedTotal = count) {
  const name = item.replace(/^minecraft:/, '').replace(/[[{].*$/, '')
  consoleCommand('give ' + NAME + ' ' + item + ' ' + count)
  await bot.waitCount(name, expectedTotal)
}

async function clear (bot) {
  consoleCommand('clear ' + NAME)
  const deadline = Date.now() + 5000
  while (bot.bot.inventory.items().length > 0 && Date.now() < deadline) await sleep(100)
  check(bot.bot.inventory.items().length === 0, 'inventaire non vide apres clear')
}

async function fillWithSand (bot) {
  consoleCommand('give ' + NAME + ' minecraft:sand 2304')
  await bot.waitCount('sand', 2304)
}

async function prepare (bot) {
  const from = logSize()
  consoleCommand('deop ' + NAME)
  await waitLog(/Made NovaBot no longer a server operator|Nothing changed\. The player is not an operator/, from)
  consoleCommand('gamemode survival ' + NAME)
  // Paisible : supprime les monstres (des slimes du monde plat tuaient le bot, Folia 1.21.11).
  const difficultyFrom = logSize()
  consoleCommand('difficulty peaceful')
  await waitLog(/[Dd]ifficulty/, difficultyFrom)
  await clear(bot)
}

// Total des pierres : inventaire + banque (les livraisons en attente sont lues en base apres l'arret).
async function stoneTotal (bot) {
  const info = await bot.bankInfo()
  return { inventory: bot.count('stone'), bank: info.stock.stone || 0 }
}

async function phaseMain () {
  let bot = await TestBot.connect()
  await prepare(bot)
  const state = { phase: 'main' }
  const failures = await runScenarios([
    ['a-depot', async () => {
      await give(bot, STONE, 64)
      await bot.command('/bank deposit ' + STONE + ' 32', /Depot de 32 stone/)
      await bot.waitCount('stone', 32)
      const info = await bot.bankInfo()
      check(info.stock.stone === 32, 'banque stone = ' + info.stock.stone)
      check(info.value === 16, 'valeur = ' + info.value + ' (attendu 32 x 0.5 = 16)')
      return 'bot 32, banque 32, valeur 16'
    }],
    ['b-retrait', async () => {
      await sleep(PAUSE)
      await bot.command('/bank withdraw ' + STONE + ' 10', /Retrait de 10 stone/)
      await bot.waitCount('stone', 42)
      const info = await bot.bankInfo()
      check(info.stock.stone === 22 && info.value === 11, 'banque ' + JSON.stringify(info))
      return 'bot 42, banque 22, valeur 11'
    }],
    ['k-classement', async () => {
      await sleep(PAUSE)
      await bot.command('/bank top', /#1 NovaBot - 11/)
      await sleep(PAUSE)
      await bot.command('/bank top player', /Classement player/)
      await sleep(PAUSE)
      await bot.command('/bank top inconnu', /Classement inconnu/)
      // Par categorie (config de test : stone et cobblestone = pierre, dirt = terre).
      await sleep(PAUSE)
      await bot.command('/bank top player pierre', /#1 NovaBot - 11/)
      await sleep(PAUSE)
      await bot.command('/bank top player terre', /Aucune banque classee/)
      await sleep(PAUSE)
      await bot.command('/bank top player metal', /Classement inconnu/)
      // Mise a jour ciblee : pas d'attente du recalcul complet (60 s).
      await sleep(PAUSE)
      await bot.command('/bank deposit ' + STONE + ' 10', /Depot de 10 stone/)
      await bot.waitCount('stone', 32)
      await sleep(PAUSE)
      await bot.command('/bank top', /#1 NovaBot - 16/)
      await sleep(PAUSE)
      await bot.command('/bank withdraw ' + STONE + ' 10', /Retrait de 10 stone/)
      await bot.waitCount('stone', 42)
      return 'top 11, type inconnu refuse, 16 apres depot sans attente, etat restaure'
    }],
    ['c-retrait-superieur-au-stock', async () => {
      await sleep(PAUSE)
      await bot.command('/bank withdraw ' + STONE + ' 100', /Stock insuffisant de stone/)
      await sleep(500)
      check(bot.count('stone') === 42, 'inventaire modifie : ' + bot.count('stone'))
      const info = await bot.bankInfo()
      check(info.stock.stone === 22, 'banque modifiee : ' + info.stock.stone)
      return 'refuse, bot 42, banque 22'
    }],
    ['d-item-renomme', async () => {
      await give(bot, renamedStone(), 5, 47)
      const renamed = bot.bot.inventory.items().find((item) => item.name === 'stone' && item.count === 5)
      check(renamed, 'pile renommee introuvable')
      await bot.bot.equip(renamed, 'hand')
      await sleep(PAUSE)
      await bot.command('/bank deposit hand', /aucun item deposable en main/)
      await sleep(PAUSE)
      await bot.command('/bank deposit ' + STONE + ' 45', /pas assez de stone deposables/)
      await sleep(500)
      check(bot.count('stone') === 47, 'inventaire modifie : ' + bot.count('stone'))
      const info = await bot.bankInfo()
      check(info.stock.stone === 22, 'banque modifiee : ' + info.stock.stone)
      return 'main et quantite refusees, 42 + 5 renommes conserves, banque 22'
    }],
    ['f-deposer-tout', async () => {
      await clear(bot)
      await give(bot, 'minecraft:dirt', 10)
      await give(bot, 'minecraft:cobblestone', 20)
      await give(bot, STONE, 5)
      await give(bot, 'minecraft:sand', 7)
      await sleep(PAUSE)
      await bot.command('/bank deposit all', /Depot de 5 stone/)
      await bot.waitCount('stone', 0)
      await bot.waitCount('dirt', 0)
      await bot.waitCount('cobblestone', 0)
      check(bot.count('sand') === 7, 'sable (non accepte) modifie : ' + bot.count('sand'))
      const info = await bot.bankInfo()
      check(info.stock.stone === 27 && info.stock.dirt === 10 && info.stock.cobblestone === 20,
        'banque ' + JSON.stringify(info.stock))
      return 'stone 27, dirt 10, cobblestone 20 en banque, sable 7 conserve'
    }],
    ['g-gui', async () => {
      await clear(bot)
      await give(bot, 'minecraft:dirt', 3)
      await sleep(PAUSE)
      const opened = new Promise((resolve) => bot.bot.once('windowOpen', resolve))
      bot.bot.chat('/bank')
      const window = await Promise.race([opened, sleep(8000).then(() => null)])
      check(window, 'GUI non ouverte')
      const title = JSON.stringify(window.title)
      check(/Banque/.test(title) && title.includes(NAME), 'titre ' + title)
      check(window.slots.length >= 54 + 36, 'taille ' + window.slots.length)
      const names = window.slots.slice(0, 45).filter(Boolean).map((item) => item.name)
      check(JSON.stringify(names) === JSON.stringify(['cobblestone', 'dirt', 'stone']), 'stock affiche ' + names)
      const stoneSlot = window.slots.findIndex((item, slot) => slot < 45 && item && item.name === 'stone')
      check(window.slots[stoneSlot].count === 27, 'quantite affichee ' + window.slots[stoneSlot].count)
      const lore = JSON.stringify(window.slots[stoneSlot].customLore || window.slots[stoneSlot].components || '')
      check(lore.includes('27'), 'description sans quantite : ' + lore.slice(0, 200))
      // Clic gauche : retrait d'une pile (27 en banque).
      await bot.bot.clickWindow(stoneSlot, 0, 0)
      await bot.waitCount('stone', 27)
      await sleep(PAUSE)
      // Clic droit sur la terre : retrait de 1.
      const dirtSlot = window.slots.findIndex((item, slot) => slot < 45 && item && item.name === 'dirt')
      await bot.bot.clickWindow(dirtSlot, 1, 0)
      await bot.waitCount('dirt', 4)
      await sleep(PAUSE * 2)
      // Tentatives de prendre une icone : clic, majuscule, touche numerique, jet, double clic,
      // majuscule depuis l'inventaire du joueur vers le coffre.
      // Case 47 = vitre de remplissage (45, le livre, ouvre la liste des prix : teste en phase prix).
      const attempts = [[47, 0, 0], [47, 0, 1], [0, 0, 2], [0, 0, 4], [49, 0, 6], [46, 1, 0]]
      const playerStone = window.slots.findIndex((item, slot) => slot >= 54 && item && item.name === 'stone')
      for (const [slot, button, mode] of attempts) {
        await bot.bot.clickWindow(slot, button, mode).catch(() => { })
        await sleep(250)
      }
      if (bot.bot.currentWindow) {
        await bot.bot.clickWindow(playerStone, 0, 1).catch(() => { })
        await sleep(500)
      }
      check(bot.bot.currentWindow, 'GUI fermee par un clic qui ne devait rien faire')
      check(bot.count('stone') === 27 && bot.count('dirt') === 4, 'inventaire apres tentatives : stone '
        + bot.count('stone') + ', dirt ' + bot.count('dirt'))
      for (const icon of GUI_ICONS) check(bot.count(icon) === 0, 'icone sortie de la GUI : ' + icon)
      // Bouton "deposer tout".
      await bot.bot.clickWindow(49, 0, 0)
      await bot.waitCount('stone', 0)
      await bot.waitCount('dirt', 0)
      await sleep(PAUSE)
      // Bouton "fermer" (clic gauche).
      const closed = new Promise((resolve) => bot.bot.once('windowClose', resolve))
      await bot.bot.clickWindow(53, 0, 0)
      check(await Promise.race([closed.then(() => true), sleep(3000).then(() => false)]), 'GUI non fermee')
      await sleep(500)
      check(bot.bot.inventory.items().length === 0, 'inventaire non vide : '
        + bot.bot.inventory.items().map((item) => item.name + 'x' + item.count))
      const info = await bot.bankInfo()
      check(info.stock.stone === 27 && info.stock.dirt === 13 && info.stock.cobblestone === 20,
        'banque ' + JSON.stringify(info.stock))
      return 'titre ' + title.slice(0, 60) + ', 3 items affiches, pile retiree (27), 1 terre retiree, '
        + 'aucune icone sortie, deposer tout, fermer'
    }],
    ['j-reset-admin', async () => {
      const from = logSize()
      consoleCommand('novabank reset ' + NAME)
      await waitLog(/Confirmez avec/, from)
      consoleCommand('novabank reset ' + NAME + ' confirm')
      await waitLog(/Valeur de la banque de NovaBot reinitialisee/, from)
      const after = await bot.bankInfo()
      check(after.value === 0, 'valeur apres reset ' + after.value)
      check(after.stock.stone === 27 && after.stock.dirt === 13, 'stock modifie ' + JSON.stringify(after.stock))
      await sleep(PAUSE)
      await bot.command('/bank withdraw ' + STONE + ' 5', /Retrait de 5 stone/)
      await sleep(PAUSE)
      await bot.command('/bank deposit ' + STONE + ' 5', /Depot de 5 stone/)
      const again = await bot.bankInfo()
      check(again.value === 0 && again.stock.stone === 27, 'apres retrait/redepot ' + JSON.stringify(again))
      return 'valeur 0, stock conserve, retrait + redepot sans valeur'
    }],
    ['e-inventaire-plein-livraison', async () => {
      await clear(bot)
      await fillWithSand(bot)
      await sleep(PAUSE)
      await bot.command('/bank withdraw ' + STONE + ' 10', /vous seront livres a votre prochaine connexion/)
      check(bot.count('stone') === 0, 'pierre recue malgre inventaire plein')
      const info = await bot.bankInfo()
      check(info.stock.stone === 17, 'banque ' + info.stock.stone)
      await clear(bot)
      await bot.quit()
      bot = await TestBot.connect()
      await bot.waitChat(/item\(s\) en attente vous ont ete livres/, 0, 10000)
      await bot.waitCount('stone', 10)
      return 'non remis (inventaire plein) -> livraison en attente, remise apres reconnexion (10)'
    }],
    ['i-deconnexion-apres-retrait', async () => {
      await sleep(PAUSE)
      const before = await stoneTotal(bot)
      await sleep(PAUSE)
      bot.bot.chat('/bank withdraw ' + STONE + ' 5')
      await bot.quit()
      bot = await TestBot.connect()
      await sleep(4000)
      const after = await stoneTotal(bot)
      state.iBefore = before
      state.iAfter = after
      check(after.inventory + after.bank === before.inventory + before.bank,
        'total ' + JSON.stringify(before) + ' -> ' + JSON.stringify(after))
      return JSON.stringify(before) + ' -> ' + JSON.stringify(after) + ' (en attente : verifie en base)'
    }],
    ['h-arret-pendant-depot', () => depositThenStop(bot, state, 0)]
  ])
  writeState(state)
  return failures
}

// Lance un depot de 64 pierres puis arrete le serveur apres le delai donne ; le total est
// verifie apres redemarrage (phase restart).
async function depositThenStop (bot, state, delayMs) {
  await clear(bot)
  await sleep(PAUSE)
  const info = await bot.bankInfo()
  await give(bot, STONE, 64)
  state.hBefore = { inventory: 64, bank: info.stock.stone || 0 }
  delete state.hAfter
  await sleep(PAUSE)
  bot.bot.chat('/bank deposit ' + STONE + ' 64')
  if (delayMs > 0) await sleep(delayMs)
  consoleCommand('stop')
  return 'depot lance puis stop ' + (delayMs > 0 ? delayMs + ' ms apres' : 'immediat') + ' ; avant '
    + JSON.stringify(state.hBefore)
}

async function phaseRestart () {
  const previous = readState()
  const bot = await TestBot.connect()
  await sleep(4000) // remise eventuelle des livraisons (2 s apres la connexion)
  const failures = await runScenarios([
    ['h-apres-redemarrage', async () => {
      const after = await stoneTotal(bot)
      previous.hAfter = after
      return 'avant ' + JSON.stringify(previous.hBefore) + ', apres ' + JSON.stringify(after)
        + ' (total avec livraisons en attente verifie en base)'
    }]
  ])
  writeState(previous)
  await bot.quit()
  return failures
}

async function phaseReturn () {
  let bot = await TestBot.connect()
  await prepare(bot)
  const state = readState()
  const failures = await runScenarios([
    ['e-inventaire-plein-retour-banque', async () => {
      await fillWithSand(bot)
      const before = await bot.bankInfo()
      await sleep(PAUSE)
      await bot.command('/bank withdraw ' + STONE + ' 10', /sont restes dans la banque/)
      check(bot.count('stone') === 0, 'pierre recue malgre inventaire plein')
      const after = await bot.bankInfo()
      check(after.stock.stone === before.stock.stone, 'banque ' + before.stock.stone + ' -> ' + after.stock.stone)
      await clear(bot)
      return 'non remis -> rendu a la banque (' + after.stock.stone + ')'
    }],
    ['i-deconnexion-retour-banque', async () => {
      await sleep(PAUSE)
      const before = await stoneTotal(bot)
      await sleep(PAUSE)
      bot.bot.chat('/bank withdraw ' + STONE + ' 5')
      await bot.quit()
      bot = await TestBot.connect()
      await sleep(4000)
      const after = await stoneTotal(bot)
      check(after.inventory + after.bank === before.inventory + before.bank,
        'total ' + JSON.stringify(before) + ' -> ' + JSON.stringify(after))
      return JSON.stringify(before) + ' -> ' + JSON.stringify(after)
    }],
    ['h2-arret-pendant-depot-engage', () => depositThenStop(bot, state, 100)]
  ])
  writeState(state)
  return failures
}

// Phase custom : un item d'un plugin d'items custom (CUSTOM_KEY, CUSTOM_GIVE avec {name}, CUSTOM_MATERIAL = materiau
// vanilla de base, CUSTOM_PRICE) est depose, retire et retrouve identique ; l'equivalent vanilla reste refuse.
async function phaseCustom () {
  const key = process.env.CUSTOM_KEY
  const material = process.env.CUSTOM_MATERIAL
  const price = Number(process.env.CUSTOM_PRICE || 5)
  const bot = await TestBot.connect()
  await prepare(bot)
  return runScenarios([
    ['custom-donner', async () => {
      consoleCommand(process.env.CUSTOM_GIVE.replace('{name}', NAME))
      await bot.waitCount(material, 5)
      return '5 ' + key + ' recus (' + material + ')'
    }],
    ['custom-vanilla-refuse', async () => {
      await sleep(PAUSE)
      await bot.command('/bank deposit minecraft:' + material + ' 1', /n'accepte pas|pas assez de|aucun item deposable/)
      await sleep(500)
      check(bot.count(material) === 5, 'inventaire modifie : ' + bot.count(material))
      return 'equivalent vanilla refuse, rien depose'
    }],
    ['custom-depot', async () => {
      await sleep(PAUSE)
      await bot.command('/bank deposit ' + key + ' 3', /Depot de 3/)
      await bot.waitCount(material, 2)
      const info = await bot.bankInfo()
      check(Object.values(info.stock).join() === '3', 'banque ' + JSON.stringify(info))
      check(info.value === 3 * price, 'valeur ' + info.value + ' (attendu ' + 3 * price + ')')
      return 'bot 2, banque 3, valeur ' + info.value
    }],
    ['custom-retrait', async () => {
      await sleep(PAUSE)
      await bot.command('/bank withdraw ' + key + ' 3', /Retrait de 3/)
      await bot.waitCount(material, 5)
      // Identique : les 5 piles se fusionnent en une seule (meme item custom), sinon elles resteraient separees.
      const piles = bot.bot.inventory.items().filter((item) => item.name === material)
      check(piles.length === 1 && piles[0].count === 5, 'piles : ' + piles.map((item) => item.count).join('+'))
      const info = await bot.bankInfo()
      check(Object.keys(info.stock).length === 0, 'banque non vide : ' + JSON.stringify(info))
      return 'retire, 5 items dans une seule pile (identiques), banque vide'
    }]
  ])
}

// Menus (fenetres de conteneur) : texte de chaque case pour reperer et cliquer un bouton. Le bot n'a pas de rendu
// graphique : le "cliche" d'un menu est un fichier texte (titre + une ligne par case non vide).
// Texte lisible d'un composant NBT simplifie (titre, nom d'item) : valeurs "text" et listes de chaines.
function plainText (node, key, out = []) {
  if (Array.isArray(node)) node.forEach((child) => plainText(child, key, out))
  else if (node && typeof node === 'object') {
    if (node.type === 'string' && key === 'text' && typeof node.value === 'string') out.push(node.value)
    else if (node.type === 'string' && Array.isArray(node.value)) out.push(...node.value)
    else Object.keys(node).forEach((k) => plainText(node[k], k, out))
  }
  return out
}

function slotLabel (item) {
  let custom = ''
  if (typeof item.customName === 'string') {
    try { custom = plainText(JSON.parse(item.customName)).join('').trim() || item.customName } catch (e) { custom = item.customName }
  } else if (item.customName) custom = plainText(item.customName).join('').trim()
  return [item.name, custom].filter(Boolean).join(' | ')
}

function dumpMenu (window, label) {
  const lines = ['menu ' + label + ' : type ' + window.type + ', titre ' + JSON.stringify(plainText(window.title).join('')) +
    ', ' + window.inventoryStart + ' cases']
  window.slots.slice(0, window.inventoryStart).forEach((item, slot) => {
    if (item && !/glass_pane$/.test(item.name)) lines.push('  case ' + slot + ' : ' + item.count + ' x ' + slotLabel(item))
  })
  const file = path.join(path.dirname(config.state), 'menu-' + label.replace(/\W+/g, '_') + '.txt')
  fs.writeFileSync(file, lines.join('\n') + '\n')
  lines.forEach((line) => console.log('[menu] ' + line))
  return file
}

// Case a cliquer : numero, ou motif cherche dans le nom des items du menu (hors inventaire du joueur).
function menuSlot (window, spec) {
  if (/^\d+$/.test(spec)) return Number(spec)
  const pattern = new RegExp(spec, 'i')
  return window.slots.slice(0, window.inventoryStart).findIndex((item) => item && pattern.test(slotLabel(item)))
}

// Phase integration : le bot cree une entite dans un plugin tiers (INTEG_CMDS, commandes separees par '|', INTEG_OP=1 pour
// etre operateur), choisit son type de banque (INTEG_TYPE), y depose 5 pierres et verifie le nom de la banque (INTEG_NAME).
async function phaseIntegration () {
  const type = process.env.INTEG_TYPE
  const name = new RegExp(process.env.INTEG_NAME)
  let bot = await TestBot.connect()
  await prepare(bot)
  if (process.env.INTEG_OP === '1') consoleCommand('op ' + NAME)
  return runScenarios([
    ['integration-creation', async () => {
      await give(bot, STONE, 10)
      if (process.env.INTEG_HOLD) { // item a tenir en main (ex. golden_shovel), donne par la console
        consoleCommand('give ' + NAME + ' minecraft:' + process.env.INTEG_HOLD + ' 1')
        await bot.waitCount(process.env.INTEG_HOLD, 1)
        await bot.bot.equip(bot.bot.inventory.items().find((item) => item.name === process.env.INTEG_HOLD), 'hand')
      }
      for (const command of (process.env.INTEG_PRE || '').split('|').filter(Boolean)) { // commandes console
        consoleCommand(command.replace('{name}', NAME))
        await sleep(500)
      }
      // Menus ouverts par les commandes : INTEG_CLICK = motifs (ou numeros de case) a cliquer, dans l'ordre.
      const menus = []
      bot.bot.on('windowOpen', (window) => menus.push(window))
      const clicks = (process.env.INTEG_CLICK || '').split('|').filter(Boolean)
      let handled = 0
      for (const command of process.env.INTEG_CMDS.split('|')) {
        await bot.command(command, null)
        await sleep(Number(process.env.INTEG_WAIT || 2500))
        while (menus.length > handled) {
          const window = menus[handled++]
          await sleep(800)
          dumpMenu(window, command + ' ' + handled)
          const spec = clicks.shift()
          if (spec === undefined) continue
          const slot = menuSlot(window, spec)
          check(slot >= 0, 'case introuvable dans le menu pour ' + spec)
          await bot.bot.clickWindow(slot, 0, 0)
          console.log('[menu] clic sur la case ' + slot)
          await sleep(Number(process.env.INTEG_WAIT || 2500))
        }
      }
      return process.env.INTEG_CMDS
    }],
    ['integration-reconnexion', async () => {
      // Les appartenances sont lues a la connexion : le joueur se reconnecte apres la creation.
      if (process.env.INTEG_RECONNECT !== '1') return 'sans reconnexion'
      await bot.quit()
      bot = await TestBot.connect()
      return 'reconnecte'
    }],
    ['integration-selection', async () => {
      await bot.command('/bank select ' + type, new RegExp('Banque active : .*\\(' + type + '\\)'), 10000)
      await sleep(PAUSE)
      const header = await bot.command('/bank info', name, 10000)
      return 'banque ' + type + ' selectionnee : ' + (Array.isArray(header) ? header.input : header)
    }],
    ['integration-depot', async () => {
      await sleep(PAUSE)
      await bot.command('/bank deposit ' + STONE + ' 5', /Depot de 5 stone/)
      await bot.waitCount('stone', 5)
      await sleep(PAUSE)
      await bot.command('/bank info', new RegExp('Banque active : .*\\(' + type + '\\)'), 10000)
      const info = await bot.bankInfo()
      check(info.stock.stone === 5 && info.value === 2.5, 'banque ' + JSON.stringify(info))
      return 'depot de 5 dans la banque de l entite, valeur 2.5'
    }],
    // Niveau d'ile de SuperiorSkyblock2 (INTEG_ISLAND_LEVEL=1 : bonus de niveau, coefficient 20) : valeur 2.5 -> niveau 50.
    ...(process.env.INTEG_ISLAND_LEVEL === '1' ? [
      ['niveau-ile-apres-depot', async () => {
        await sleep(3000)
        const start = bot.messages.length
        await bot.command('/is level', null)
        await sleep(3000)
        const out = bot.messages.slice(start).join(' | ')
        console.log('[niveau] ' + out)
        check(/\b50(\.0+)?\b/.test(out), 'niveau 50 attendu : ' + out)
        return 'niveau de l ile = valeur de la banque x 20 : ' + out
      }],
      ['niveau-ile-apres-retrait', async () => {
        await bot.command('/bank withdraw ' + STONE + ' 5', /Retrait de 5 stone/)
        await sleep(3000)
        const start = bot.messages.length
        await bot.command('/is level', null)
        await sleep(3000)
        const out = bot.messages.slice(start).join(' | ')
        console.log('[niveau] ' + out)
        check(!/\b50(\.0+)?\b/.test(out), 'niveau toujours 50 : ' + out)
        return 'banque videe, niveau revenu : ' + out
      }]
    ] : [])
  ])
}

// Phase roles : un second joueur (NovaBot2) rejoint l'entite creee par NovaBot (INTEG_CMDS, INTEG_INVITE pour NovaBot,
// INTEG_ACCEPT pour NovaBot2). Droits attendus par defaut : le membre depose mais ne retire pas, le proprietaire fait les deux.
async function phaseRoles () {
  const type = process.env.INTEG_TYPE
  const header = new RegExp('Banque active : .*\\(' + type + '\\)')
  const second = NAME + '2'
  const owner = await TestBot.connect()
  await prepare(owner)
  if (process.env.INTEG_OP === '1') consoleCommand('op ' + NAME)
  const member = await TestBot.connect(second)
  consoleCommand('gamemode survival ' + second)
  consoleCommand('clear ' + second)
  await sleep(800)
  return runScenarios([
    ['roles-creation-et-invitation', async () => {
      await give(owner, STONE, 10)
      for (const command of process.env.INTEG_CMDS.split('|')) {
        await owner.command(command, null)
        await sleep(Number(process.env.INTEG_WAIT || 2500))
      }
      await owner.command(process.env.INTEG_INVITE, null)
      await sleep(2000)
      await member.command(process.env.INTEG_ACCEPT, null)
      await sleep(2500)
      return 'invite et accepte'
    }],
    ['roles-proprietaire-depot', async () => {
      await owner.command('/bank select ' + type, header, 10000)
      await sleep(PAUSE)
      await owner.command('/bank deposit ' + STONE + ' 5', /Depot de 5 stone/)
      await owner.waitCount('stone', 5)
      return 'proprietaire : depot de 5'
    }],
    ['roles-membre-voit-la-meme-banque', async () => {
      await member.command('/bank select ' + type, header, 10000)
      await sleep(PAUSE)
      await member.command('/bank info', /stone : 5/, 10000)
      return 'le membre voit 5 pierres dans la banque commune'
    }],
    ['roles-membre-depot-autorise', async () => {
      consoleCommand('give ' + second + ' minecraft:stone 10')
      await member.waitCount('stone', 10)
      await sleep(PAUSE)
      await member.command('/bank deposit ' + STONE + ' 5', /Depot de 5 stone/)
      await member.waitCount('stone', 5)
      await owner.command('/bank info', /stone : 10/, 10000)
      return 'membre : depot de 5, banque a 10'
    }],
    ['roles-membre-retrait-refuse', async () => {
      await sleep(PAUSE)
      await member.command('/bank withdraw ' + STONE + ' 3', /role ne vous permet pas/, 10000)
      await sleep(500)
      check(member.count('stone') === 5, 'inventaire du membre modifie : ' + member.count('stone'))
      await owner.command('/bank info', /stone : 10/, 10000)
      return 'retrait du membre refuse, banque inchangee (10)'
    }],
    ['roles-proprietaire-retrait', async () => {
      await sleep(PAUSE)
      await owner.command('/bank withdraw ' + STONE + ' 3', /Retrait de 3 stone/)
      await owner.waitCount('stone', 8)
      await member.command('/bank info', /stone : 7/, 10000)
      return 'proprietaire : retrait de 3, banque a 7 (vu par le membre)'
    }]
  ])
}

// Phase guiedit : l'administrateur deplace un bouton dans l'editeur visuel du GUI (/novabank gui), ferme (le brouillon
// reste), enregistre ; le fichier, la sauvegarde et le GUI de banque doivent refleter le changement.
async function phaseGuiEdit () {
  const bot = await TestBot.connect()
  await prepare(bot)
  const opFrom = logSize()
  consoleCommand('op ' + NAME)
  await waitLog(/Made NovaBot a server operator/, opFrom)
  const windows = []
  bot.bot.on('windowOpen', (window) => windows.push(window))
  const base = path.resolve(path.dirname(config.state), '../../plugins/NovaBank')
  const waitWindow = async (count) => {
    const deadline = Date.now() + 8000
    while (windows.length < count && Date.now() < deadline) await sleep(100)
    check(windows.length >= count, 'fenetre non ouverte')
    await sleep(800)
    return windows[windows.length - 1]
  }
  return runScenarios([
    ['gui-ouverture', async () => {
      await bot.command('/novabank gui', null)
      const window = await waitWindow(1)
      dumpMenu(window, 'editeur')
      check(JSON.stringify(window.title).includes('editeur de GUI'), 'titre : ' + JSON.stringify(window.title))
      check(window.slots[49] && window.slots[49].name === 'hopper', 'bouton Tout deposer absent de la case 49')
      return 'editeur ouvert, bouton Tout deposer en case 49'
    }],
    ['gui-deplacement', async () => {
      const window = windows[0]
      await bot.bot.clickWindow(49, 0, 0)
      await sleep(600)
      await bot.bot.clickWindow(4, 0, 0)
      await sleep(600)
      check(window.slots[4] && window.slots[4].name === 'hopper', 'bouton non deplace en case 4')
      check(!window.slots[49] || window.slots[49].name !== 'hopper', 'ancienne case 49 encore occupee par le bouton')
      return 'bouton deplace de la case 49 a la case 4 (dans la zone de stock)'
    }],
    ['gui-fermeture-garde-le-brouillon', async () => {
      bot.bot.closeWindow(windows[0])
      await bot.waitChat(/Brouillon conserve/, 0, 8000)
      return 'brouillon conserve a la fermeture'
    }],
    ['gui-enregistrement-sauvegarde-et-fichier', async () => {
      const before = fs.existsSync(path.join(base, 'backup')) ? fs.readdirSync(path.join(base, 'backup')).length : 0
      const oldYaml = fs.readFileSync(path.join(base, 'config.yml'), 'utf8')
      await bot.command('/novabank gui save', /Disposition du GUI enregistree/, 15000)
      await sleep(500)
      const yaml = fs.readFileSync(path.join(base, 'config.yml'), 'utf8')
      check(/deposit-all:\s+slot: 4\b/.test(yaml), 'slot 4 absent du fichier')
      const backups = fs.readdirSync(path.join(base, 'backup')).filter((name) => name.endsWith('-gui.yml'))
      check(fs.readdirSync(path.join(base, 'backup')).length === before + 1 && backups.length >= 1, 'sauvegarde absente')
      const saved = fs.readFileSync(path.join(base, 'backup', backups.sort().pop()), 'utf8')
      check(saved === oldYaml, 'la sauvegarde ne contient pas l\'ancien fichier')
      return 'fichier ecrit (slot 4), ancien fichier sauvegarde dans backup/'
    }],
    ['gui-banque-reflete-le-changement', async () => {
      await bot.command('/bank', null)
      const window = await waitWindow(2)
      dumpMenu(window, 'banque')
      check(window.slots[4] && window.slots[4].name === 'hopper', 'bouton Tout deposer absent de la case 4 de la banque')
      check(!window.slots[49] || window.slots[49].name !== 'hopper', 'ancien emplacement encore occupe')
      return 'le GUI de banque affiche Tout deposer en case 4 apres rechargement'
    }]
  ])
}

// Phase itemsedit : l'administrateur edite la liste des items acceptes et leurs prix (commandes et coffre), enregistre ;
// le fichier, la sauvegarde et un vrai depot au nouveau prix doivent le refleter.
async function phaseItemsEdit () {
  const bot = await TestBot.connect()
  await prepare(bot)
  const opFrom = logSize()
  consoleCommand('op ' + NAME)
  await waitLog(/Made NovaBot a server operator/, opFrom)
  const windows = []
  bot.bot.on('windowOpen', (window) => windows.push(window))
  const base = path.resolve(path.dirname(config.state), '../../plugins/NovaBank')
  const read = () => fs.readFileSync(path.join(base, 'config.yml'), 'utf8')
  return runScenarios([
    ['items-commandes-sur-le-brouillon', async () => {
      await bot.command('/novabank items add diamond_block 90 minerais', /Brouillon : minecraft:diamond_block = 90/)
      await sleep(PAUSE)
      await bot.command('/novabank items price stone 2', /Brouillon : minecraft:stone = 2/)
      await sleep(PAUSE)
      await bot.command('/novabank items remove cobblestone', /Brouillon : - minecraft:cobblestone/)
      await sleep(PAUSE)
      await bot.command('/novabank items price dirt abc', /Prix invalide/)
      await sleep(PAUSE)
      await bot.command('/novabank items price iron_block 1', /Item absent du brouillon/)
      check(!read().includes('diamond_block'), 'le fichier a ete modifie avant save')
      return 'ajout, prix, retrait ; erreurs refusees ; fichier intact avant save'
    }],
    ['items-coffre-clic-gauche-augmente-le-prix', async () => {
      await bot.command('/novabank items', null)
      const deadline = Date.now() + 8000
      while (windows.length < 1 && Date.now() < deadline) await sleep(100)
      check(windows.length === 1, 'coffre non ouvert')
      await sleep(800)
      const window = windows[0]
      dumpMenu(window, 'items')
      check(JSON.stringify(window.title).includes('items acceptes'), 'titre : ' + JSON.stringify(window.title))
      check(window.slots[0].name === 'stone', 'premiere case : ' + window.slots[0].name)
      await bot.bot.clickWindow(0, 0, 0) // ouvre l'ecran de prix de stone
      const deadline2 = Date.now() + 8000
      while (windows.length < 2 && Date.now() < deadline2) await sleep(100)
      check(windows.length === 2, 'ecran de prix non ouvert')
      await sleep(800)
      const price = windows[1]
      check(JSON.stringify(price.title).includes('prix de l'), 'titre : ' + JSON.stringify(price.title))
      await bot.bot.clickWindow(15, 0, 0)
      await sleep(600)
      await bot.bot.clickWindow(15, 0, 0)
      await sleep(600)
      bot.bot.closeWindow(price)
      await bot.waitChat(/Brouillon conserve/, 0, 8000)
      return 'stone ouvre l\'ecran de prix, deux fois +0.50 : 2 -> 3, brouillon conserve a la fermeture'
    }],
    ['items-enregistrement-sauvegarde-et-fichier', async () => {
      const old = read()
      await bot.command('/novabank items save', /Items enregistres/, 15000)
      await sleep(500)
      const yaml = read()
      check(/key: minecraft:stone\s+price: "3"/.test(yaml), 'prix 3 absent du fichier')
      check(/key: minecraft:diamond_block\s+price: "90"\s+category: minerais/.test(yaml), 'diamond_block absent')
      check(!yaml.includes('cobblestone'), 'cobblestone encore presente')
      const backups = fs.readdirSync(path.join(base, 'backup')).filter((name) => name.endsWith('-items.yml'))
      check(backups.length >= 1 && fs.readFileSync(path.join(base, 'backup', backups.sort().pop()), 'utf8') === old,
        'sauvegarde absente ou differente de l\'ancien fichier')
      return 'fichier ecrit, ancien fichier sauvegarde'
    }],
    ['items-depot-au-nouveau-prix', async () => {
      await give(bot, STONE, 10)
      await bot.command('/bank deposit ' + STONE + ' 10', /Depot de 10 stone/)
      consoleCommand('give ' + NAME + ' minecraft:diamond_block 1')
      await bot.waitCount('diamond_block', 1)
      await sleep(PAUSE)
      await bot.command('/bank deposit minecraft:diamond_block 1', /Depot de 1 diamond_block/)
      const info = await bot.bankInfo()
      check(info.value === 120, 'valeur ' + info.value + ' (attendu 10 x 3 + 90 = 120)')
      return 'valeur 120 = 10 pierres a 3 + 1 bloc de diamant a 90'
    }],
    ['items-cancel-abandonne-le-brouillon', async () => {
      await bot.command('/novabank items add dirt 99', /Brouillon/)
      await sleep(PAUSE)
      await bot.command('/novabank items cancel', /Brouillon abandonne/)
      await sleep(PAUSE)
      await bot.command('/novabank items save', /Aucun changement a enregistrer/)
      check(!read().includes('"99"'), 'le fichier contient le brouillon abandonne')
      return 'brouillon abandonne, rien enregistre'
    }]
  ])
}

// Phase hub : parcours complet a la souris depuis /bank admin (menu principal) : items, GUI, lignes, enregistrement.
async function phaseHub () {
  const bot = await TestBot.connect()
  await prepare(bot)
  const opFrom = logSize()
  consoleCommand('op ' + NAME)
  await waitLog(/Made NovaBot a server operator/, opFrom)
  const windows = []
  const chats = []
  bot.bot.on('windowOpen', (window) => windows.push(window))
  bot.bot.on('message', (message) => chats.push(JSON.stringify(message.json || message)))
  const base = path.resolve(path.dirname(config.state), '../../plugins/NovaBank')
  const read = () => fs.readFileSync(path.join(base, 'config.yml'), 'utf8')
  const waitWindow = async (count, label) => {
    const deadline = Date.now() + 8000
    while (windows.length < count && Date.now() < deadline) await sleep(100)
    check(windows.length >= count, 'fenetre non ouverte : ' + label)
    await sleep(800)
    return windows[windows.length - 1]
  }
  const click = async (slot) => { await bot.bot.clickWindow(slot, 0, 0); await sleep(700) }
  const title = (window) => JSON.stringify(window.title)
  const label = (window, slot) => window.slots[slot] ? slotLabel(window.slots[slot]) : '(vide)'
  return runScenarios([
    ['hub-ouverture', async () => {
      await bot.command('/bank admin', null)
      const hub = await waitWindow(1, 'menu principal')
      dumpMenu(hub, 'hub')
      check(title(hub).includes('administration'), 'titre : ' + title(hub))
      check(hub.slots[10].name === 'emerald' && hub.slots[12].name === 'chest', 'boutons items et gui absents')
      return 'menu principal ouvert : Items et prix (10), Disposition du GUI (12)'
    }],
    ['hub-items-puis-retour-avec-echap', async () => {
      await click(10)
      const items = await waitWindow(2, 'editeur d\'items')
      check(title(items).includes('items acceptes'), 'titre : ' + title(items))
      check(items.slots[0].name === 'stone', 'premiere case : ' + items.slots[0].name)
      await click(0) // ouvre l'ecran de prix de stone
      const price = await waitWindow(3, 'ecran de prix')
      check(title(price).includes('prix de l'), 'titre : ' + title(price))
      await click(15)
      await click(15) // +0.50 deux fois
      bot.bot.closeWindow(price)
      const back = await waitWindow(4, 'retour au menu')
      check(title(back).includes('administration'), 'retour au menu principal : ' + title(back))
      check(/Enregistrer et deployer/.test(label(back, 27)), 'bouton Enregistrer non actif : ' + label(back, 27))
      return 'editeur d\'items, ecran de prix +1 (2 x 0.50), Echap ramene au menu principal'
    }],
    ['hub-gui-deplacement-puis-retour', async () => {
      await click(12)
      const editor = await waitWindow(5, 'editeur de GUI')
      check(title(editor).includes('editeur de GUI'), 'titre : ' + title(editor))
      await click(49)
      await click(4)
      check(editor.slots[4] && editor.slots[4].name === 'hopper', 'bouton non deplace')
      bot.bot.closeWindow(editor)
      const back = await waitWindow(6, 'retour au menu')
      check(title(back).includes('administration'), 'retour au menu principal')
      return 'Tout deposer deplace en case 4, Echap ramene au menu principal'
    }],
    ['hub-lignes-puis-enregistrement', async () => {
      const hub = windows[windows.length - 1]
      await click(14) // clic gauche : 6 lignes, +1 refuse (maximum)
      await bot.bot.clickWindow(14, 1, 0)
      await sleep(700)
      check(JSON.stringify(hub.slots[14].customName).includes('5'), 'lignes : ' + label(hub, 14))
      const old = read()
      let n = windows.length
      await click(31) // Abandonner : ecran de confirmation, on annule
      const ask = await waitWindow(n + 1, 'confirmation')
      check(title(ask).includes('confirmation'), 'titre : ' + title(ask))
      await click(0) // ANNULER : retour au menu, brouillon intact
      const again = await waitWindow(n + 2, 'retour apres annulation')
      check(/Enregistrer et deployer/.test(label(again, 27)), 'brouillon perdu : ' + label(again, 27))
      n = windows.length
      await click(27) // Enregistrer et deployer : confirmation
      await waitWindow(n + 1, 'confirmation')
      check(read() === old, 'fichier modifie avant la confirmation')
      await click(6) // CONFIRMER
      await bot.waitChat(/Changements enregistres/, 0, 15000)
      await sleep(500)
      const yaml = read()
      check(/rows: 5\b/.test(yaml), 'rows: 5 absent du fichier')
      check(/deposit-all:\s+slot: 4\b/.test(yaml), 'slot 4 absent')
      check(/key: minecraft:stone\s+price: "1.5"/.test(yaml), 'prix 1.5 absent')
      const backups = fs.readdirSync(path.join(base, 'backup')).filter((name) => name.endsWith('-admin.yml'))
      check(backups.length >= 1 && fs.readFileSync(path.join(base, 'backup', backups.sort().pop()), 'utf8') === old,
        'sauvegarde absente ou differente')
      return 'une seule ecriture : 5 lignes, bouton en case 4, prix 1.5 ; ancien fichier sauvegarde'
    }],
    ['hub-le-vrai-gui-de-banque-reflete-tout', async () => {
      bot.bot.closeWindow(windows[windows.length - 1])
      await sleep(1500)
      const count = windows.length
      await bot.command('/bank', null)
      const bank = await waitWindow(count + 1, 'GUI de banque')
      dumpMenu(bank, 'banque')
      check(bank.inventoryStart === 45, 'lignes du GUI : ' + (bank.inventoryStart / 9))
      check(bank.slots[4] && bank.slots[4].name === 'hopper', 'Tout deposer absent de la case 4')
      return 'le GUI de banque a 5 lignes et Tout deposer en case 4'
    }],
    ['hub-ajout-d-un-item-par-clic-dans-l-inventaire', async () => {
      bot.bot.closeWindow(windows[windows.length - 1]) // la banque de la scene precedente est encore ouverte
      await sleep(1500)
      await give(bot, 'minecraft:glowstone_dust', 1)
      await sleep(PAUSE)
      const count = windows.length
      await bot.command('/bank admin', null)
      await waitWindow(count + 1, 'menu principal')
      await click(10)
      const items = await waitWindow(count + 2, 'editeur d\'items')
      check(!items.slots.slice(0, 45).some((item) => item && item.name === 'glowstone_dust'), 'glowstone_dust deja dans la liste')
      const mine = items.slots.findIndex((item, slot) => slot >= items.inventoryStart && item && item.name === 'glowstone_dust')
      check(mine >= 0, 'glowstone_dust absent de l\'inventaire du bot')
      await click(mine)
      check(items.slots.slice(0, 45).some((item) => item && item.name === 'glowstone_dust'), 'glowstone_dust non ajoute a la liste')
      bot.bot.closeWindow(items)
      return 'un clic sur glowstone_dust dans l\'inventaire l\'ajoute a la liste des items (prix 1)'
    }],
    ['accueil-novabank-cliquable', async () => {
      await sleep(1500)
      chats.length = 0
      await bot.command('/novabank', null)
      await sleep(1200)
      const all = chats.join('\n')
      for (const needle of ['YoannFM', 'v2026.11.23 (1.0.1 #1)', 'discord.gg/HtjPAfqUXu', 'github.com/YoannFm/NovaBank', '/bank deposit', '/novabank reload']) {
        check(all.includes(needle), 'absent de l\'accueil : ' + needle)
      }
      check(/suggest_command/.test(all), 'aucune commande cliquable (suggest_command)')
      check(/open_url/.test(all), 'aucun lien cliquable (open_url)')
      check(!/run_command/.test(all), 'une commande s\'execute au clic (run_command)')
      check(all.indexOf('YoannFM') < all.indexOf('v2026.11.23') && all.indexOf('v2026.11.23') < all.indexOf('Description') &&
        all.indexOf('Description') < all.indexOf('discord.gg') && all.indexOf('discord.gg') < all.indexOf('github.com'), 'ordre de l\'accueil')
      return 'accueil complet, commandes en suggest_command, liens en open_url'
    }],
    ['accueil-novabank-sans-droit-admin', async () => {
      const from = logSize()
      consoleCommand('deop ' + NAME)
      await waitLog(/Made NovaBot no longer a server operator/, from)
      await sleep(800)
      chats.length = 0
      await bot.command('/novabank', null)
      await sleep(1200)
      let all = chats.join('\n')
      check(all.includes('/bank deposit') && all.includes('/novabank help'), 'aide joueur absente')
      check(!all.includes('/novabank reload') && !all.includes('/bank admin') && !all.includes('/novabank config'), 'commandes admin visibles sans droit')
      chats.length = 0
      await bot.command('/novabank reload', null)
      await sleep(1000)
      all = chats.join('\n')
      check(/permission/i.test(all), 'reload non refuse : ' + all.slice(0, 200))
      return 'sans droit : aide joueur seulement, /novabank reload refuse'
    }]
  ])
}

// Menu de banque : inventaire plein (rien n'est retire), liste des prix (livre), item passe a prix 0 puis retire du catalogue.
async function phasePrix () {
  const bot = await TestBot.connect()
  await prepare(bot)
  const windows = []
  bot.bot.on('windowOpen', (window) => windows.push(window))
  const configFile = path.resolve(path.dirname(config.state), '../../plugins/NovaBank/config.yml')
  const waitWindow = async (count, label) => {
    const deadline = Date.now() + 8000
    while (windows.length < count && Date.now() < deadline) await sleep(100)
    check(windows.length >= count, 'fenetre non ouverte : ' + label)
    await sleep(800)
    return windows[windows.length - 1]
  }
  // Titre : composant (1.20.3+) ou chaine JSON (avant) : on cherche dans les deux formes.
  const title = (window) => plainText(window.title).join('') + ' ' + JSON.stringify(window.title)
  const lore = (item) => JSON.stringify(item.customLore || item.components || item.nbt || '')
  const stockSlot = (window, name) => window.slots.findIndex((item, slot) => slot < 45 && item && item.name === name)
  const closeMenu = async (window) => { bot.bot.closeWindow(window); await sleep(PAUSE) }
  const editConfig = async (change) => {
    fs.writeFileSync(configFile, change(fs.readFileSync(configFile, 'utf8')))
    const from = logSize()
    consoleCommand('novabank reload')
    await waitLog(/Configuration rechargee/, from)
  }
  const value = async (expected) => {
    const info = await bot.bankInfo()
    check(Math.abs(info.value - expected) < 0.001, 'valeur ' + info.value + ' (attendu ' + expected + ')')
    return info
  }
  return runScenarios([
    ['p-depot', async () => {
      await give(bot, STONE, 64)
      await sleep(PAUSE)
      await bot.command('/bank deposit ' + STONE + ' 64', /Depot de 64 stone/)
      await give(bot, 'minecraft:dirt', 20)
      await sleep(PAUSE)
      await bot.command('/bank deposit minecraft:dirt 20', /Depot de 20 dirt/)
      await value(34)
      return 'stone 64 (0.5) + dirt 20 (0.1) = 34'
    }],
    ['p-inventaire-plein-menu', async () => {
      await fillWithSand(bot)
      await sleep(PAUSE)
      await bot.command('/bank', null)
      const menu = await waitWindow(windows.length + 1, 'banque')
      const slot = stockSlot(menu, 'stone')
      const start = bot.messages.length
      for (const button of [0, 1]) {
        const from = bot.messages.length
        await bot.bot.clickWindow(slot, button, 0)
        await bot.waitChat(/Plus de place/, from)
        await sleep(PAUSE)
      }
      await closeMenu(menu)
      check(bot.count('stone') === 0, 'pierre recue malgre inventaire plein')
      check(!bot.messages.slice(start).some((line) => /prochaine connexion/.test(line)), 'livraison differee creee')
      const info = await value(34)
      check(info.stock.stone === 64, 'stock ' + info.stock.stone)
      await clear(bot)
      return 'clic gauche et clic droit : "Plus de place", stock 64 intact, aucune livraison differee'
    }],
    ['p-pile-bornee-par-la-place', async () => {
      await give(bot, 'minecraft:sand', 2240) // 35 piles : une seule case libre
      await give(bot, STONE, 54)
      await sleep(PAUSE)
      await bot.command('/bank', null)
      const menu = await waitWindow(windows.length + 1, 'banque')
      await bot.bot.clickWindow(stockSlot(menu, 'stone'), 0, 0)
      await bot.waitCount('stone', 64)
      await sleep(PAUSE)
      await closeMenu(menu)
      const info = await value(29)
      check(info.stock.stone === 54, 'stock ' + info.stock.stone)
      await clear(bot)
      return 'place pour 10 : clic gauche retire 10 (pas 64), stock 54'
    }],
    ['p-livre-liste-des-prix', async () => {
      await bot.command('/bank', null)
      const menu = await waitWindow(windows.length + 1, 'banque')
      const before = windows.length
      await bot.bot.clickWindow(45, 0, 0)
      const prices = await waitWindow(before + 1, 'liste des prix')
      dumpMenu(prices, 'prix')
      check(title(prices).includes('Ce que rapporte'), 'titre : ' + title(prices))
      const names = prices.slots.slice(0, 45).filter(Boolean).map((item) => item.name)
      check(['stone', 'dirt', 'cobblestone'].every((name) => names.includes(name)), 'items : ' + names)
      check(lore(prices.slots[stockSlot(prices, 'stone')]).includes('Rapporte'), 'lore sans prix')
      await bot.bot.clickWindow(49, 0, 0)
      const back = await waitWindow(before + 2, 'retour a la banque')
      check(title(back).includes('Banque'), 'retour : ' + title(back))
      await closeMenu(back)
      return 'livre -> liste (stone, dirt, cobblestone avec prix) -> retour a la banque'
    }],
    ['p-prix-zero', async () => {
      await editConfig((text) => text.replace(/(key: minecraft:dirt\n\s+price: )"0\.1"/, '$1"0"'))
      await value(27)
      await give(bot, 'minecraft:dirt', 5)
      await sleep(PAUSE)
      await bot.command('/bank deposit minecraft:dirt 5', /Depot de 5 dirt/)
      const info = await value(27)
      check(info.stock.dirt === 25, 'dirt ' + info.stock.dirt)
      await bot.command('/bank', null)
      const menu = await waitWindow(windows.length + 1, 'banque')
      const before = windows.length
      await bot.bot.clickWindow(45, 0, 0)
      const prices = await waitWindow(before + 1, 'liste des prix')
      check(lore(prices.slots[stockSlot(prices, 'dirt')]).includes('Ne rapporte rien'), 'dirt sans mention prix nul')
      await closeMenu(prices)
      return 'prix 0 : valeur 29 -> 27 (DERIVED), depot encore accepte sans valeur, liste "Ne rapporte rien"'
    }],
    ['p-item-retire-du-catalogue', async () => {
      await editConfig((text) => text.replace(/  - key: minecraft:dirt\n(    .*\n)+/, ''))
      const info = await value(27)
      check(info.stock.dirt === 25, 'stock dirt perdu : ' + JSON.stringify(info.stock))
      await give(bot, 'minecraft:dirt', 3)
      await sleep(PAUSE)
      await bot.command('/bank deposit minecraft:dirt 3', /n'accepte pas/)
      await sleep(PAUSE)
      await bot.command('/bank withdraw minecraft:dirt 5', /Retrait de 5 dirt/)
      await bot.waitCount('dirt', 8)
      await bot.command('/bank', null)
      const menu = await waitWindow(windows.length + 1, 'banque')
      check(stockSlot(menu, 'dirt') >= 0, 'dirt absent du menu de banque')
      const before = windows.length
      await bot.bot.clickWindow(45, 0, 0)
      const prices = await waitWindow(before + 1, 'liste des prix')
      check(stockSlot(prices, 'dirt') < 0, 'dirt encore dans la liste des prix')
      await closeMenu(prices)
      const after = await value(27)
      check(after.stock.dirt === 20, 'dirt ' + after.stock.dirt)
      return 'retire : stock 25 conserve et visible, depot refuse, retrait 5 OK (reste 20), absent de la liste des prix'
    }]
  ])
}

// Menu des parametres (comparateur du menu d'administration) : nombre, oui/non, secret saisi au chat, roles,
// enregistrement unique (fichier, commentaires conserves, sauvegarde), valeur appliquee.
async function phaseParams () {
  const bot = await TestBot.connect()
  await prepare(bot)
  const opFrom = logSize()
  consoleCommand('op ' + NAME)
  await waitLog(/Made NovaBot a server operator/, opFrom)
  const windows = []
  bot.bot.on('windowOpen', (window) => windows.push(window))
  const base = path.resolve(path.dirname(config.state), '../../plugins/NovaBank')
  const read = () => fs.readFileSync(path.join(base, 'config.yml'), 'utf8')
  const comments = (text) => text.split('\n').filter((line) => line.trim().startsWith('#')).length
  const SECRET = 'S3cretNova"Bank!'
  const after = async (count, label) => {
    const deadline = Date.now() + 8000
    while (windows.length <= count && Date.now() < deadline) await sleep(100)
    check(windows.length > count, 'fenetre non ouverte : ' + label)
    await sleep(700)
    return windows[windows.length - 1]
  }
  const open = async (slot, label, button = 0) => {
    const n = windows.length
    await bot.bot.clickWindow(slot, button, 0)
    return after(n, label)
  }
  const click = async (slot, button = 0) => { await bot.bot.clickWindow(slot, button, 0); await sleep(600) }
  const title = (window) => plainText(window.title).join('') + ' ' + JSON.stringify(window.title)
  const label = (window, slot) => window.slots[slot] ? slotLabel(window.slots[slot]) : '(vide)'
  const lore = (item) => JSON.stringify(item ? (item.customLore || item.components || item.nbt || '') : '')
  let old = ''
  return runScenarios([
    ['s-comparateur', async () => {
      old = read()
      const n = windows.length
      await bot.command('/bank admin', null)
      const hub = await after(n, 'menu principal')
      check(hub.slots[29] && hub.slots[29].name === 'comparator', 'case 29 : ' + label(hub, 29))
      const home = await open(29, 'parametres')
      dumpMenu(home, 'parametres')
      check(title(home).includes('parametres'), 'titre : ' + title(home))
      const names = [10, 11, 12, 13, 14, 15, 16, 21, 23].map((slot) => home.slots[slot] && home.slots[slot].name)
      check(names.every(Boolean), 'categories : ' + names)
      return '9 categories : ' + names.join(', ')
    }],
    ['s-nombre-et-oui-non', async () => {
      const page = await open(12, 'limites')
      dumpMenu(page, 'limites')
      check(lore(page.slots[1]).includes('500'), 'delai initial : ' + lore(page.slots[1]).slice(0, 120))
      await click(1)
      await click(1)
      check(lore(page.slots[1]).includes('700'), 'delai apres 2 clics : ' + lore(page.slots[1]).slice(0, 120))
      await click(2)
      check(lore(page.slots[2]).includes('Non'), 'sauvegarde inventaire non basculee')
      check(read() === old, 'fichier modifie avant l\'enregistrement')
      await open(49, 'retour accueil')
      return 'delai 500 -> 700 (2 clics), sauvegarde de l\'inventaire -> Non, fichier intact'
    }],
    ['s-secret-au-chat', async () => {
      const page = await open(21, 'stockage')
      check(label(page, 5).includes('Mot de passe SQL'), 'case 5 : ' + label(page, 5))
      const closed = new Promise((resolve) => bot.bot.once('windowClose', resolve))
      const from = bot.messages.length
      const n = windows.length
      await bot.bot.clickWindow(5, 0, 0)
      await Promise.race([closed, sleep(3000)])
      await bot.waitChat(/Tapez le secret/, from)
      const logBefore = logSize()
      bot.bot.chat(SECRET)
      await bot.waitChat(/Brouillon : Mot de passe SQL modifie/, from, 8000)
      const back = await after(n, 'retour a la page stockage')
      check(lore(back.slots[5]).includes('********'), 'secret non masque')
      check(!lore(back.slots[5]).includes('S3cret'), 'secret affiche dans le menu')
      check(!fs.readFileSync(config.log, 'utf8').slice(logBefore).includes('S3cret'), 'secret ecrit dans la console')
      check(!bot.messages.slice(from).some((line) => line.includes('S3cret')), 'secret renvoye dans le chat')
      await open(49, 'retour accueil')
      return 'secret tape au chat : intercepte, masque (********), absent de la console et du chat'
    }],
    ['s-roles', async () => {
      const banks = await open(11, 'banques')
      const roles = await open(4, 'roles')
      dumpMenu(roles, 'roles')
      check(roles.slots[7] && roles.slots[7].name === 'gray_dye', 'retrait member deja autorise : ' + label(roles, 7))
      await click(7)
      check(roles.slots[7] && roles.slots[7].name === 'lime_dye', 'retrait member non autorise apres clic')
      await open(49, 'retour banques')
      check(banks, 'page banques')
      await open(49, 'retour accueil')
      return 'joueur : retrait autorise au role member (gris -> vert)'
    }],
    ['s-enregistrement', async () => {
      const home = windows[windows.length - 1]
      const hub = await open(31, 'retour menu principal')
      check(title(hub).includes('administration'), 'retour : ' + title(hub))
      check(/Enregistrer et deployer/.test(label(hub, 27)), 'Enregistrer non actif : ' + label(hub, 27))
      check(home, 'accueil')
      await open(27, 'confirmation')
      const from = bot.messages.length
      await bot.bot.clickWindow(6, 0, 0)
      await bot.waitChat(/Changements enregistres/, from, 15000)
      await sleep(800)
      const yaml = read()
      check(/cooldown-ms: 700\b/.test(yaml), 'cooldown-ms: 700 absent')
      check(/save-player-data: false\b/.test(yaml), 'save-player-data: false absent')
      check(yaml.includes('password: "S3cretNova\\"Bank!"'), 'mot de passe absent ou mal echappe')
      check(/withdraw: \["owner", "admin", "member"\]/.test(yaml), 'roles absents')
      check(comments(yaml) === comments(old), 'commentaires perdus : ' + comments(old) + ' -> ' + comments(yaml))
      const backups = fs.readdirSync(path.join(base, 'backup')).filter((name) => name.endsWith('-admin.yml'))
      check(backups.length >= 1 && fs.readFileSync(path.join(base, 'backup', backups.sort().pop()), 'utf8') === old,
        'sauvegarde absente ou differente')
      check(bot.messages.slice(from).some((line) => /redemarrage necessaire/.test(line)), 'avertissement de redemarrage absent')
      return 'une ecriture : delai 700, sauvegarde Non, mot de passe echappe, roles ; ' + comments(yaml) +
        ' commentaires conserves ; sauvegarde backup/ ; redemarrage signale (stockage)'
    }],
    ['s-valeur-appliquee', async () => {
      const from = bot.messages.length
      await bot.command('/novabank config get limits.cooldown-ms', /limits.cooldown-ms = 700/)
      await bot.command('/novabank config get storage.sql.password', /storage.sql.password = /)
      check(!bot.messages.slice(from).some((line) => line.includes('S3cret')), 'secret lisible par config get')
      return 'rechargement : delai 700 en vigueur ; config get masque le mot de passe'
    }]
  ])
}

async function main () {
  const phase = process.argv[2]
  const phases = { main: phaseMain, restart: phaseRestart, return: phaseReturn, custom: phaseCustom, integration: phaseIntegration, roles: phaseRoles, guiedit: phaseGuiEdit, itemsedit: phaseItemsEdit, hub: phaseHub, prix: phasePrix, params: phaseParams }
  if (!phases[phase]) throw new Error('Phase inconnue : ' + phase + ' (main, restart, return)')
  const failures = await phases[phase]()
  await sleep(500)
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((error) => {
  console.log('RESULT ' + (process.argv[2] || '?') + '-bot FAIL ' + error.message)
  process.exit(2)
})

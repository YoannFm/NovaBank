'use strict'
// Bot de la validation multi-serveur (E7e) : un joueur au meme nom sur deux serveurs partage la
// meme banque (UUID hors ligne derive du nom). Il depose puis retire des pierres une par une.
// Variables : SYNC_DEPOSITS, SYNC_WITHDRAWS (defaut 0). Une ligne RESULT sync-<nom> par bot.
const { config, sleep, consoleCommand, logSize, waitLog, TestBot, runScenarios } = require('./harness')

const NAME = config.username
const PAUSE = 700 // > delai entre deux operations (500 ms)

async function main () {
  const deposits = Number(process.env.SYNC_DEPOSITS || 0)
  const withdraws = Number(process.env.SYNC_WITHDRAWS || 0)
  const bot = await TestBot.connect()
  const from = logSize()
  consoleCommand('deop ' + NAME)
  await waitLog(/no longer a server operator|not an operator/, from)
  consoleCommand('gamemode survival ' + NAME)
  consoleCommand('clear ' + NAME)
  await sleep(800)
  const failures = await runScenarios([
    ['sync-' + config.port + '-depots-puis-retraits', async () => {
      if (deposits > 0) {
        consoleCommand('give ' + NAME + ' minecraft:stone ' + deposits)
        await bot.waitCount('stone', deposits, 15000)
      }
      for (let i = 0; i < deposits; i++) {
        await bot.command('/bank deposit minecraft:stone 1', /Depot de 1 stone/, 15000)
        await sleep(PAUSE)
      }
      for (let i = 0; i < withdraws; i++) {
        await bot.command('/bank withdraw minecraft:stone 1', /Retrait de 1 stone/, 15000)
        await sleep(PAUSE)
      }
      return deposits + ' depot(s), ' + withdraws + ' retrait(s)'
    }],
    ...(process.env.SYNC_TOP_EXPECT ? [['sync-' + config.port + '-classement', async () => {
      // Le classement de CE serveur doit refleter les operations faites sur l'autre (Redis ou lecture de la base).
      const expected = new RegExp('#1 ' + NAME + ' - ' + process.env.SYNC_TOP_EXPECT + '(\\D|$)')
      for (let attempt = 1; ; attempt++) {
        try {
          await bot.command('/bank top', expected, 4000)
          return 'valeur ' + process.env.SYNC_TOP_EXPECT + ' apres ' + attempt + ' essai(s)'
        } catch (missing) {
          if (attempt >= 8) throw missing
          await sleep(3000)
        }
      }
    }]] : [])
  ])
  await bot.quit()
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((error) => {
  console.log('RESULT sync-bot FAIL ' + error.message)
  process.exit(2)
})

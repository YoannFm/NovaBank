'use strict'
// Outils du bot de test NovaBank : connexion mineflayer, console du serveur (FIFO),
// lecture du journal du serveur, attente des messages de chat, comptage de l'inventaire.
// Les dependances (mineflayer) sont installees hors du projet : NODE_PATH les fournit.
const fs = require('fs')
const mineflayer = require('mineflayer')

const env = (name) => {
  const value = process.env[name]
  if (!value) throw new Error('Variable d\'environnement manquante : ' + name)
  return value
}

const config = {
  host: '127.0.0.1',
  port: Number(env('BOT_PORT')),
  version: env('BOT_MC_VERSION'),
  username: process.env.BOT_NAME || 'NovaBot',
  fifo: env('BOT_FIFO'),
  log: env('BOT_LOG'),
  state: env('BOT_STATE')
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

let fifoFd = null
function consoleCommand (command) {
  if (fifoFd === null) fifoFd = fs.openSync(config.fifo, 'w')
  fs.writeSync(fifoFd, command + '\n')
  console.log('[console] > ' + command)
}

function logSize () {
  return fs.statSync(config.log).size
}

function stripAnsi (text) {
  return text.replace(/\u001b\[[0-9;]*m/g, '').replace(/§./g, '')
}

// Attend un motif dans le journal du serveur ecrit apres la position donnee.
async function waitLog (pattern, from, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const size = logSize()
    if (size > from) {
      const fd = fs.openSync(config.log, 'r')
      const buffer = Buffer.alloc(size - from)
      fs.readSync(fd, buffer, 0, buffer.length, from)
      fs.closeSync(fd)
      const text = stripAnsi(buffer.toString('utf8'))
      const match = text.match(pattern)
      if (match) return { match, text }
    }
    await sleep(200)
  }
  throw new Error('Motif absent du journal du serveur : ' + pattern)
}

class TestBot {
  constructor (bot) {
    this.bot = bot
    this.messages = []
    bot.on('messagestr', (text) => {
      this.messages.push(text)
      console.log('[chat] ' + text)
    })
  }

  static async connect (username = config.username) {
    const bot = mineflayer.createBot({
      host: config.host,
      port: config.port,
      username,
      version: config.version,
      auth: 'offline',
      hideErrors: false
    })
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Connexion du bot trop longue')), 30000)
      bot.once('spawn', () => { clearTimeout(timer); resolve() })
      bot.once('kicked', (reason) => { clearTimeout(timer); reject(new Error('Bot expulse : ' + JSON.stringify(reason))) })
      bot.once('error', (error) => { clearTimeout(timer); reject(error) })
    })
    const testBot = new TestBot(bot)
    await sleep(1500)
    return testBot
  }

  // Envoie une commande et attend le premier message de chat correspondant au motif.
  async command (text, pattern, timeoutMs = 8000) {
    const start = this.messages.length
    this.bot.chat(text)
    console.log('[bot] > ' + text)
    if (!pattern) return null
    return this.waitChat(pattern, start, timeoutMs)
  }

  async waitChat (pattern, start = 0, timeoutMs = 8000) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      for (let i = start; i < this.messages.length; i++) {
        const match = this.messages[i].match(pattern)
        if (match) return match
      }
      await sleep(100)
    }
    throw new Error('Message attendu absent : ' + pattern + ' (recus : ' + JSON.stringify(this.messages.slice(start)) + ')')
  }

  // Unites d'un item dans l'inventaire principal (36 cases), piles renommees comprises.
  count (name) {
    return this.bot.inventory.items().filter((item) => item.name === name).reduce((sum, item) => sum + item.count, 0)
  }

  async waitCount (name, expected, timeoutMs = 6000) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      if (this.count(name) === expected) return
      await sleep(100)
    }
    throw new Error('Inventaire : ' + name + ' = ' + this.count(name) + ', attendu ' + expected)
  }

  // Consultation de la banque par /bank info : quantites par nom court et valeur.
  async bankInfo () {
    const start = this.messages.length
    this.bot.chat('/bank info')
    await this.waitChat(/Valeur : |La banque est vide|aucune banque/, start)
    await sleep(400)
    const lines = this.messages.slice(start)
    const stock = {}
    let value = null
    for (const line of lines) {
      const stockLine = line.match(/^\s*- (\S+) : ([\d\s,.]+)$/)
      if (stockLine) stock[stockLine[1]] = Number(stockLine[2].replace(/[\s,.]/g, ''))
      const valueLine = line.match(/Valeur : ([\d\s,.]+)/)
      if (valueLine) value = Number(valueLine[1].replace(/\s/g, '').replace(',', '.'))
    }
    return { stock, value }
  }

  async quit () {
    const ended = new Promise((resolve) => this.bot.once('end', resolve))
    this.bot.quit()
    await Promise.race([ended, sleep(5000)])
    await sleep(1000)
  }
}

function readState () {
  try {
    return JSON.parse(fs.readFileSync(config.state, 'utf8'))
  } catch (missing) {
    return {}
  }
}

function writeState (state) {
  fs.writeFileSync(config.state, JSON.stringify(state, null, 2))
}

function check (condition, message) {
  if (!condition) throw new Error(message)
}

// Execute les scenarios dans l'ordre ; une ligne RESULT par scenario.
async function runScenarios (scenarios) {
  let failures = 0
  for (const [id, scenario] of scenarios) {
    const start = Date.now()
    try {
      const detail = await scenario()
      console.log('RESULT ' + id + ' PASS ' + (detail || '') + ' (' + (Date.now() - start) + ' ms)')
    } catch (error) {
      failures++
      console.log('RESULT ' + id + ' FAIL ' + error.message)
    }
  }
  return failures
}

module.exports = { config, sleep, consoleCommand, logSize, waitLog, TestBot, readState, writeState, check, runScenarios }

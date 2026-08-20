#!/usr/bin/env node
// QA simulator: drives a real, visible Chrome window through the master's
// screens end to end — create a narrate-only room, manually deal names and
// roles (forcing whichever roles you want to watch), start the game, and
// randomly play through night/day phases until GAME_OVER.
//
// Usage examples:
//   npm run sim
//   npm run sim -- --players 10 --werewolves 2 --force PRIEST
//   npm run sim -- --force PRIEST --force GRAVEDIGGER --delay 900
//   npm run sim -- --help
import { chromium } from 'playwright'
import { parseArgs } from 'node:util'

const ROLE_META = {
  WEREWOLF: { label: 'Lupi mannari', min: 1, max: 99, inAltri: false },
  PRIEST: { label: 'Sacerdoti', min: 0, max: 99, inAltri: false },
  GRAVEDIGGER: { label: 'Becchini', min: 0, max: 99, inAltri: true },
  IDIOT: { label: 'Idioti', min: 0, max: 99, inAltri: true },
  CORRUPTED_JUDGE: { label: 'Giudice corrotto', min: 0, max: 1, inAltri: true },
  SURVIVOR: { label: 'Sopravvissuti', min: 0, max: 99, inAltri: true },
}
const DEFAULT_COUNTS = { WEREWOLF: 1, PRIEST: 1, GRAVEDIGGER: 0, IDIOT: 0, CORRUPTED_JUDGE: 0, SURVIVOR: 0 }
const MIN_PLAYERS = 4
const MAX_PLAYERS = 30
const NAME_POOL = [
  'ALICE', 'BOB', 'CHARLIE', 'DAVE', 'EVE', 'FRANK', 'GRACE', 'HEIDI', 'IVAN', 'JUDY',
  'KEN', 'LEO', 'MONA', 'NINA', 'OSCAR', 'PAUL', 'QUINN', 'RITA', 'STEVE', 'TARA',
  'UMA', 'VIC', 'WENDY', 'XENA', 'YURI', 'ZARA', 'AMOS', 'BEA', 'CARL', 'DINA',
]

function parseCli() {
  const { values } = parseArgs({
    options: {
      'base-url': { type: 'string', default: 'http://lupus.localhost' },
      players: { type: 'string', default: '8' },
      werewolves: { type: 'string', default: String(DEFAULT_COUNTS.WEREWOLF) },
      priests: { type: 'string', default: String(DEFAULT_COUNTS.PRIEST) },
      gravediggers: { type: 'string', default: String(DEFAULT_COUNTS.GRAVEDIGGER) },
      idiots: { type: 'string', default: String(DEFAULT_COUNTS.IDIOT) },
      'corrupted-judges': { type: 'string', default: String(DEFAULT_COUNTS.CORRUPTED_JUDGE) },
      survivors: { type: 'string', default: String(DEFAULT_COUNTS.SURVIVOR) },
      force: { type: 'string', multiple: true, default: [] },
      delay: { type: 'string', default: '500' },
      seed: { type: 'string' },
      help: { type: 'boolean', default: false },
    },
  })

  if (values.help) {
    console.log(`
QA simulator — drives the master flow in a real browser.

  --base-url <url>          default http://lupus.localhost
  --players <n>             default 8 (4-30)
  --werewolves <n>          default 1
  --priests <n>             default 1
  --gravediggers <n>        default 0
  --idiots <n>              default 0
  --corrupted-judges <n>    default 0 (0-1)
  --survivors <n>           default 0
  --force <ROLE>            force this role onto a specific player; repeatable
                             (e.g. --force PRIEST --force GRAVEDIGGER)
  --delay <ms>              pause between simulated actions, default 500
  --seed <n>                seed the random shuffle/decisions for a reproducible run
  --help                    show this help
`)
    process.exit(0)
  }

  const counts = {
    WEREWOLF: Number(values.werewolves),
    PRIEST: Number(values.priests),
    GRAVEDIGGER: Number(values.gravediggers),
    IDIOT: Number(values.idiots),
    CORRUPTED_JUDGE: Number(values['corrupted-judges']),
    SURVIVOR: Number(values.survivors),
  }
  const forced = values.force.map((r) => r.trim().toUpperCase())

  return {
    baseUrl: values['base-url'],
    playerCount: Number(values.players),
    counts,
    forced,
    delay: Number(values.delay),
    seed: values.seed !== undefined ? Number(values.seed) : Date.now(),
  }
}

function mulberry32(seed) {
  let a = seed >>> 0
  return function random() {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function validate(config) {
  const errors = []
  if (config.playerCount < MIN_PLAYERS || config.playerCount > MAX_PLAYERS) {
    errors.push(`--players must be between ${MIN_PLAYERS} and ${MAX_PLAYERS}`)
  }
  for (const [role, count] of Object.entries(config.counts)) {
    const meta = ROLE_META[role]
    if (count < meta.min || count > meta.max) {
      errors.push(`${role} count must be between ${meta.min} and ${meta.max}, got ${count}`)
    }
  }
  for (const role of config.forced) {
    if (!ROLE_META[role] && role !== 'VILLAGER') {
      errors.push(`--force ${role}: unknown role. Valid: ${Object.keys(ROLE_META).join(', ')}, VILLAGER`)
    }
  }
  const specialTotal = Object.values(config.counts).reduce((a, b) => a + b, 0)
  if (specialTotal > config.playerCount) {
    errors.push(`Configured special roles (${specialTotal}) exceed the player count (${config.playerCount})`)
  }
  if (errors.length) {
    console.error('Config error(s):\n' + errors.map((e) => ` - ${e}`).join('\n'))
    process.exit(1)
  }
}

/** Auto-bump a role's configured count so every forced instance has a slot to be dealt into. */
function reconcileForcedCounts(config) {
  const bumped = []
  for (const role of config.forced) {
    const needed = config.forced.filter((r) => r === role).length
    if (config.counts[role] !== undefined && config.counts[role] < needed) {
      bumped.push(`${role}: ${config.counts[role]} -> ${needed}`)
      config.counts[role] = needed
    }
  }
  if (bumped.length) {
    console.log(`Bumped role counts to fit forced roles: ${bumped.join(', ')}`)
  }
}

/** Builds the final name -> role assignment: forced roles first, then the remaining pool shuffled. */
function buildAssignment(config, random) {
  if (config.playerCount > NAME_POOL.length) {
    throw new Error(`Name pool only has ${NAME_POOL.length} names, need ${config.playerCount}`)
  }
  const names = NAME_POOL.slice(0, config.playerCount)

  const pool = []
  for (const [role, count] of Object.entries(config.counts)) {
    for (let i = 0; i < count; i++) pool.push(role)
  }
  while (pool.length < config.playerCount) pool.push('VILLAGER')

  const assignment = []
  for (const role of config.forced) {
    const idx = pool.indexOf(role)
    if (idx === -1) throw new Error(`No remaining ${role} slot to force (pool exhausted)`)
    pool.splice(idx, 1)
    assignment.push({ name: names[assignment.length], role, forced: true })
  }

  // Fisher-Yates shuffle of what's left, using the seeded RNG.
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[pool[i], pool[j]] = [pool[j], pool[i]]
  }
  for (const role of pool) {
    assignment.push({ name: names[assignment.length], role, forced: false })
  }

  return assignment
}

async function clickTimes(locator, times, pace) {
  for (let i = 0; i < times; i++) {
    await locator.click()
    await locator.page().waitForTimeout(pace)
  }
}

async function runWizard(page, config, delay) {
  await page.goto(`${config.baseUrl}/create`)

  // Step 1: mode (Classica is already selected).
  await page.getByRole('button', { name: 'Avanti', exact: true }).click()

  // Step 2: participation.
  await page.getByLabel('Inserisco io i giocatori, nessuno usa il telefono').check()
  await page.getByRole('button', { name: 'Avanti', exact: true }).click()

  // Step 3: assignment.
  await page.getByLabel('Manuale, scelgo io il ruolo di ogni giocatore').check()
  await page.getByRole('button', { name: 'Avanti', exact: true }).click()

  // Step 4: player count.
  const playerDelta = config.playerCount - 8
  const playerBtn = page.getByRole('button', { name: playerDelta >= 0 ? 'Aumenta Giocatori' : 'Diminuisci Giocatori' })
  await clickTimes(playerBtn, Math.abs(playerDelta), 60)
  await page.getByRole('button', { name: 'Avanti', exact: true }).click()

  // Step 5: roles.
  const needsAltri = Object.entries(config.counts).some(
    ([role, count]) => ROLE_META[role].inAltri && count !== DEFAULT_COUNTS[role],
  )
  if (needsAltri) {
    await page.locator('.altri-toggle').click()
  }
  for (const [role, count] of Object.entries(config.counts)) {
    const meta = ROLE_META[role]
    const delta = count - DEFAULT_COUNTS[role]
    if (delta === 0) continue
    const btn = page.getByRole('button', { name: (delta > 0 ? 'Aumenta ' : 'Diminuisci ') + meta.label })
    await clickTimes(btn, Math.abs(delta), 60)
  }

  await page.getByRole('button', { name: 'Crea partita', exact: true }).click()
  await page.waitForURL(/\/room\/[A-Z0-9]+\/roster/)

  const code = new URL(page.url()).pathname.split('/')[2]
  console.log(`Room created: ${code}`)
  return code
}

async function dealRoster(page, assignment, delay) {
  for (const { name, role } of assignment) {
    await page.getByPlaceholder('Nome giocatore').fill(name)
    await page.locator('select').selectOption({ value: role })
    await page.getByRole('button', { name: 'Aggiungi', exact: true }).click()
    await page.waitForTimeout(delay)
  }
  await page.getByRole('button', { name: 'Inizia partita', exact: true }).click()
  await page.waitForURL(/\/room\/[A-Z0-9]+\/game/)
  await page.locator('.game-card').waitFor()
}

async function playGame(page, delay, random) {
  const avanti = () => page.getByRole('button', { name: 'Avanti', exact: true })
  let step = 0
  const MAX_STEPS = 300

  while (true) {
    step += 1
    if (step > MAX_STEPS) throw new Error(`Simulation exceeded ${MAX_STEPS} steps — likely stuck.`)

    if ((await avanti().count()) === 0) break // GAME_OVER

    const title = (await page.locator('.game-card-title').textContent())?.trim() ?? ''
    const round = (await page.locator('.game-round').textContent().catch(() => null))?.trim()
    console.log(`[${round ?? '-'}] ${title}`)

    const tokens = page.locator('.game-selection-grid .game-selectable-token')
    const tokenCount = await tokens.count()
    if (tokenCount > 0) {
      const required = await avanti().isDisabled()
      if (required || random() < 0.5) {
        const idx = Math.floor(random() * tokenCount)
        const label = (await tokens.nth(idx).locator('.player-token-name').textContent())?.trim()
        console.log(`  -> selecting ${label}`)
        await tokens.nth(idx).click()
      } else {
        console.log('  -> skipping selection')
      }
    }

    await avanti().click()
    await page.waitForTimeout(delay)
  }

  const title = (await page.locator('.game-card-title').textContent())?.trim() ?? ''
  const body = (await page.locator('.game-card-body').textContent())?.trim() ?? ''
  console.log(`\n=== GAME OVER === ${title} — ${body}`)
}

async function main() {
  const config = parseCli()
  validate(config)
  reconcileForcedCounts(config)
  const random = mulberry32(config.seed)
  const assignment = buildAssignment(config, random)

  console.log(`Seed: ${config.seed}`)
  console.log('Assignment:')
  for (const { name, role, forced } of assignment) {
    console.log(`  ${name.padEnd(8)} ${role}${forced ? '  <- forced (watch this one)' : ''}`)
  }

  const browser = await chromium.launch({ headless: false, args: ['--window-position=20,20'] })
  const page = await browser.newPage({ viewport: { width: 420, height: 680 } })

  try {
    await runWizard(page, config, config.delay)
    await dealRoster(page, assignment, config.delay)
    await playGame(page, config.delay, random)
    console.log('\nSimulation complete. Browser stays open — press Ctrl+C to exit.')
  } catch (err) {
    console.error('\nSimulation failed:', err)
    await page.screenshot({ path: new URL('./error.png', import.meta.url).pathname }).catch(() => {})
    console.error('Screenshot saved to tools/qa-sim/error.png. Browser stays open — press Ctrl+C to exit.')
  }

  await new Promise(() => {})
}

main()

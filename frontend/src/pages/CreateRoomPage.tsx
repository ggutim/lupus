import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, createRoom, type GameMode } from '../api/rooms'
import { storeMasterToken } from '../api/masterToken'
import { MAX_PLAYERS, MIN_PLAYERS } from '../api/gameRules'
import BoardPanel from '../components/BoardPanel'
import InfoDialog from '../components/InfoDialog'
import QuestSteps from '../components/QuestSteps'
import RoleCard from '../components/RoleCard'
import { ROLE_ALIGNMENT } from '../roleAlignment'
import { ROLE_LABELS } from '../roleLabels'
import { ROLE_SETUP_INFO, type AssignableRole } from '../roleSetupInfo'
import { MeepleIcon, MoonIcon } from '../components/icons'

type Step = 'mode' | 'participation' | 'assignment' | 'players' | 'roles'

/**
 * The "Altri ruoli" collapsible section's roles — capped-at-one-or-zero
 * special roles beyond werewolf/priest, which get their own top-level
 * cards. Kept as one config-driven list (rather than a `useState` +
 * `RoleCard` pair per role) so adding a role here means adding one row,
 * not touching four separate places.
 */
type OtherRole = 'GRAVEDIGGER' | 'IDIOT' | 'CORRUPTED_JUDGE' | 'SURVIVOR' | 'GUARDIAN' | 'KILLER' | 'MAYOR'

const OTHER_ROLES: { role: OtherRole; label: string; max?: number }[] = [
  { role: 'GRAVEDIGGER', label: 'Becchini', max: 1 },
  { role: 'IDIOT', label: 'Idioti' },
  { role: 'CORRUPTED_JUDGE', label: 'Giudice corrotto', max: 1 },
  { role: 'SURVIVOR', label: 'Sopravvissuti' },
  { role: 'GUARDIAN', label: 'Guardiani', max: 1 },
  { role: 'KILLER', label: 'Killer', max: 1 },
  { role: 'MAYOR', label: 'Sindaco', max: 1 },
]

const OTHER_ROLE_DEFAULTS: Record<OtherRole, number> = {
  GRAVEDIGGER: 0,
  IDIOT: 0,
  CORRUPTED_JUDGE: 0,
  SURVIVOR: 0,
  GUARDIAN: 0,
  KILLER: 0,
  MAYOR: 0,
}

function CreateRoomPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('mode')

  const [gameMode, setGameMode] = useState<GameMode>('CLASSIC')
  const [remoteJoin, setRemoteJoin] = useState(true)
  const [manualRoles, setManualRoles] = useState(false)
  const [playerCount, setPlayerCount] = useState(8)
  const [werewolfCount, setWerewolfCount] = useState(1)
  const [priestCount, setPriestCount] = useState(1)
  const [otherRoleCounts, setOtherRoleCounts] = useState<Record<OtherRole, number>>(OTHER_ROLE_DEFAULTS)
  const [otherRolesOpen, setOtherRolesOpen] = useState(false)
  const [modeInfoOpen, setModeInfoOpen] = useState(false)
  const [infoRole, setInfoRole] = useState<AssignableRole | null>(null)

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const steps: { key: Step; label: string }[] = remoteJoin
    ? [
        { key: 'mode', label: 'Modalità' },
        { key: 'participation', label: 'Accesso' },
        { key: 'players', label: 'Giocatori' },
        { key: 'roles', label: 'Ruoli' },
      ]
    : [
        { key: 'mode', label: 'Modalità' },
        { key: 'participation', label: 'Accesso' },
        { key: 'assignment', label: 'Assegna' },
        { key: 'players', label: 'Giocatori' },
        { key: 'roles', label: 'Ruoli' },
      ]

  const otherRolesCount = Object.values(otherRoleCounts).reduce((sum, count) => sum + count, 0)
  const specialRoleCount = werewolfCount + priestCount + otherRolesCount
  const villagerCount = Math.max(playerCount - specialRoleCount, 0)
  const rolesExceedPlayers = specialRoleCount > playerCount

  const handleOtherRoleChange = (role: OtherRole, value: number) => {
    setOtherRoleCounts((prev) => ({ ...prev, [role]: value }))
  }

  const handleCreateRoom = async () => {
    setError(null)
    setSubmitting(true)
    try {
      const room = await createRoom({
        gameMode,
        playerCount,
        werewolfCount,
        priestCount,
        gravediggerCount: otherRoleCounts.GRAVEDIGGER,
        idiotCount: otherRoleCounts.IDIOT,
        corruptedJudgeCount: otherRoleCounts.CORRUPTED_JUDGE,
        survivorCount: otherRoleCounts.SURVIVOR,
        guardianCount: otherRoleCounts.GUARDIAN,
        killerCount: otherRoleCounts.KILLER,
        mayorCount: otherRoleCounts.MAYOR,
        remoteJoin,
        manualRoles,
      })
      storeMasterToken(room.code, room.masterToken)
      navigate(room.remoteJoin ? `/room/${room.code}` : `/room/${room.code}/roster`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Impossibile creare la stanza. Riprova.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <BoardPanel>
      <h1>Crea partita</h1>

      <QuestSteps steps={steps} current={step} />

      {step === 'mode' && (
        <section className="wizard-step">
          <h2>Scegli la modalità di gioco</h2>
          <div className="option-list">
            <label className="option">
              <input
                type="radio"
                name="gameMode"
                checked={gameMode === 'CLASSIC'}
                onChange={() => setGameMode('CLASSIC')}
              />
              Classica
            </label>
            <div className="option">
              <input
                type="radio"
                id="mode-afterlife"
                name="gameMode"
                checked={gameMode === 'AFTERLIFE'}
                onChange={() => setGameMode('AFTERLIFE')}
              />
              <label htmlFor="mode-afterlife" className="option-label">
                Aldilà
              </label>
              <button
                type="button"
                className="info-btn"
                onClick={() => setModeInfoOpen(true)}
                aria-label="Cos'è la modalità Aldilà"
              >
                i
              </button>
            </div>
          </div>
          <div className="wizard-actions">
            <Link to="/" className="button">
              Torna indietro
            </Link>
            <button type="button" className="button button-primary" onClick={() => setStep('participation')}>
              Avanti
            </button>
          </div>
        </section>
      )}

      {step === 'participation' && (
        <section className="wizard-step">
          <h2>Come partecipano i giocatori?</h2>
          <div className="option-list">
            <label className="option">
              <input type="radio" name="remoteJoin" checked={remoteJoin} onChange={() => setRemoteJoin(true)} />
              I giocatori si uniscono da remoto, con un codice
            </label>
            <label className="option">
              <input
                type="radio"
                name="remoteJoin"
                checked={!remoteJoin}
                onChange={() => setRemoteJoin(false)}
              />
              Inserisco io i giocatori, nessuno usa il telefono
            </label>
          </div>
          <div className="wizard-actions">
            <button type="button" className="button" onClick={() => setStep('mode')}>
              Indietro
            </button>
            <button
              type="button"
              className="button button-primary"
              onClick={() => setStep(remoteJoin ? 'players' : 'assignment')}
            >
              Avanti
            </button>
          </div>
        </section>
      )}

      {step === 'assignment' && (
        <section className="wizard-step">
          <h2>Come vuoi assegnare i ruoli?</h2>
          <div className="option-list">
            <label className="option">
              <input
                type="radio"
                name="manualRoles"
                checked={!manualRoles}
                onChange={() => setManualRoles(false)}
              />
              Casuale
            </label>
            <label className="option">
              <input type="radio" name="manualRoles" checked={manualRoles} onChange={() => setManualRoles(true)} />
              Manuale, scelgo io il ruolo di ogni giocatore
            </label>
          </div>
          <div className="wizard-actions">
            <button type="button" className="button" onClick={() => setStep('participation')}>
              Indietro
            </button>
            <button type="button" className="button button-primary" onClick={() => setStep('players')}>
              Avanti
            </button>
          </div>
        </section>
      )}

      {step === 'players' && (
        <section className="wizard-step">
          <h2>Numero di giocatori</h2>
          <div className="role-cards">
            <RoleCard
              icon={<MeepleIcon />}
              label="Giocatori"
              count={playerCount}
              min={MIN_PLAYERS}
              max={MAX_PLAYERS}
              onChange={setPlayerCount}
            />
          </div>
          <p>Da {MIN_PLAYERS} a {MAX_PLAYERS} giocatori.</p>
          <div className="wizard-actions">
            <button
              type="button"
              className="button"
              onClick={() => setStep(remoteJoin ? 'participation' : 'assignment')}
            >
              Indietro
            </button>
            <button
              type="button"
              className="button button-primary"
              disabled={playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS}
              onClick={() => setStep('roles')}
            >
              Avanti
            </button>
          </div>
        </section>
      )}

      {step === 'roles' && (
        <section className="wizard-step">
          <h2>Assegna i ruoli</h2>
          <div className="role-cards">
            <RoleCard
              icon={ROLE_SETUP_INFO.WEREWOLF.icon}
              label="Lupi mannari"
              align={ROLE_ALIGNMENT.WEREWOLF}
              count={werewolfCount}
              min={1}
              onChange={setWerewolfCount}
              onInfoClick={() => setInfoRole('WEREWOLF')}
            />
            <RoleCard
              icon={ROLE_SETUP_INFO.PRIEST.icon}
              label="Sacerdoti"
              align={ROLE_ALIGNMENT.PRIEST}
              count={priestCount}
              min={0}
              max={1}
              onChange={setPriestCount}
              onInfoClick={() => setInfoRole('PRIEST')}
            />

            <details className="altri-roles" open={otherRolesOpen} onToggle={(e) => setOtherRolesOpen(e.currentTarget.open)}>
              <summary>
                <div className="altri-toggle">
                  Altri ruoli
                  {otherRolesCount > 0 && ` (${otherRolesCount})`}
                  <span className="altri-chev">▾</span>
                </div>
              </summary>
              <div className="role-cards">
                {OTHER_ROLES.map(({ role, label, max }) => (
                  <RoleCard
                    key={role}
                    icon={ROLE_SETUP_INFO[role].icon}
                    label={label}
                    align={ROLE_ALIGNMENT[role]}
                    count={otherRoleCounts[role]}
                    min={0}
                    max={max}
                    onChange={(value) => handleOtherRoleChange(role, value)}
                    onInfoClick={() => setInfoRole(role)}
                  />
                ))}
              </div>
            </details>

            <RoleCard
              icon={ROLE_SETUP_INFO.VILLAGER.icon}
              label="Contadini"
              align={ROLE_ALIGNMENT.VILLAGER}
              count={villagerCount}
              readOnly
              onInfoClick={() => setInfoRole('VILLAGER')}
            />
          </div>
          {rolesExceedPlayers && (
            <p className="error">Il numero di ruoli speciali supera il numero di giocatori.</p>
          )}
          {error && <p className="error">{error}</p>}
          <div className="wizard-actions">
            <button type="button" className="button" onClick={() => setStep('players')}>
              Indietro
            </button>
            <button
              type="button"
              className="button button-primary"
              disabled={rolesExceedPlayers || submitting}
              onClick={handleCreateRoom}
            >
              {submitting ? 'Creazione in corso…' : 'Crea partita'}
            </button>
          </div>
        </section>
      )}

      <InfoDialog open={modeInfoOpen} onClose={() => setModeInfoOpen(false)} icon={<MoonIcon />} title="Modalità Aldilà">
        <p>
          Anche i giocatori morti continuano a giocare. Chi muore malvagio diventa un <strong>fantasma</strong>; chi
          muore buono diventa un <strong>angelo</strong>. Gli idioti, essendo neutrali, restano semplicemente morti.
        </p>
        <ul>
          <li>
            <strong>Fantasmi</strong> — ogni notte, insieme, maledicono due giocatori vivi per quella notte sola: al
            sacerdote appaiono con l'allineamento invertito.
          </li>
          <li>
            <strong>Angeli</strong> — ogni notte proteggono un giocatore vivo dai lupi mannari (non dal giudice
            corrotto). Se proteggono un maledetto, quel giocatore non potrà più essere protetto in futuro.
          </li>
        </ul>
        <p>
          L'ordine della notte cambia: <em>giudice corrotto → becchino → fantasmi → angeli → lupi mannari →
          sacerdote.</em> Fantasmi e angeli si svegliano solo se c'è già almeno un morto.
        </p>
      </InfoDialog>

      <InfoDialog
        open={infoRole !== null}
        onClose={() => setInfoRole(null)}
        icon={infoRole ? ROLE_SETUP_INFO[infoRole].icon : null}
        align={infoRole ? ROLE_ALIGNMENT[infoRole] : undefined}
        title={infoRole ? ROLE_LABELS[infoRole] : ''}
      >
        <p>{infoRole && ROLE_SETUP_INFO[infoRole].description}</p>
      </InfoDialog>
    </BoardPanel>
  )
}

export default CreateRoomPage

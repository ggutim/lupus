import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, createRoom, type GameMode } from '../api/rooms'
import { storeMasterToken } from '../api/masterToken'
import { MAX_PLAYERS, MIN_PLAYERS } from '../api/gameRules'
import BoardPanel from '../components/BoardPanel'
import QuestSteps from '../components/QuestSteps'
import RoleCard from '../components/RoleCard'
import { ROLE_ALIGNMENT } from '../roleAlignment'
import {
  CorruptedJudgeIcon,
  GravediggerIcon,
  IdiotIcon,
  MeepleIcon,
  PriestIcon,
  SurvivorIcon,
  VillagerIcon,
  WerewolfIcon,
} from '../components/icons'

type Step = 'mode' | 'participation' | 'assignment' | 'players' | 'roles'

function CreateRoomPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('mode')

  const [gameMode, setGameMode] = useState<GameMode>('CLASSIC')
  const [remoteJoin, setRemoteJoin] = useState(true)
  const [manualRoles, setManualRoles] = useState(false)
  const [playerCount, setPlayerCount] = useState(8)
  const [werewolfCount, setWerewolfCount] = useState(1)
  const [priestCount, setPriestCount] = useState(1)
  const [gravediggerCount, setGravediggerCount] = useState(0)
  const [idiotCount, setIdiotCount] = useState(0)
  const [corruptedJudgeCount, setCorruptedJudgeCount] = useState(0)
  const [survivorCount, setSurvivorCount] = useState(0)
  const [otherRolesOpen, setOtherRolesOpen] = useState(false)

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

  const otherRolesCount = gravediggerCount + idiotCount + corruptedJudgeCount + survivorCount
  const specialRoleCount = werewolfCount + priestCount + otherRolesCount
  const villagerCount = Math.max(playerCount - specialRoleCount, 0)
  const rolesExceedPlayers = specialRoleCount > playerCount

  const handleCreateRoom = async () => {
    setError(null)
    setSubmitting(true)
    try {
      const room = await createRoom({
        gameMode,
        playerCount,
        werewolfCount,
        priestCount,
        gravediggerCount,
        idiotCount,
        corruptedJudgeCount,
        survivorCount,
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
              icon={<WerewolfIcon />}
              label="Lupi mannari"
              align={ROLE_ALIGNMENT.WEREWOLF}
              count={werewolfCount}
              min={1}
              onChange={setWerewolfCount}
            />
            <RoleCard
              icon={<PriestIcon />}
              label="Sacerdoti"
              align={ROLE_ALIGNMENT.PRIEST}
              count={priestCount}
              min={0}
              onChange={setPriestCount}
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
                <RoleCard
                  icon={<GravediggerIcon />}
                  label="Becchini"
                  align={ROLE_ALIGNMENT.GRAVEDIGGER}
                  count={gravediggerCount}
                  min={0}
                  onChange={setGravediggerCount}
                />
                <RoleCard
                  icon={<IdiotIcon />}
                  label="Idioti"
                  align={ROLE_ALIGNMENT.IDIOT}
                  count={idiotCount}
                  min={0}
                  onChange={setIdiotCount}
                />
                <RoleCard
                  icon={<CorruptedJudgeIcon />}
                  label="Giudice corrotto"
                  align={ROLE_ALIGNMENT.CORRUPTED_JUDGE}
                  count={corruptedJudgeCount}
                  min={0}
                  max={1}
                  onChange={setCorruptedJudgeCount}
                />
                <RoleCard
                  icon={<SurvivorIcon />}
                  label="Sopravvissuti"
                  align={ROLE_ALIGNMENT.SURVIVOR}
                  count={survivorCount}
                  min={0}
                  onChange={setSurvivorCount}
                />
              </div>
            </details>

            <RoleCard
              icon={<VillagerIcon />}
              label="Contadini"
              align={ROLE_ALIGNMENT.VILLAGER}
              count={villagerCount}
              readOnly
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
    </BoardPanel>
  )
}

export default CreateRoomPage

import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, createRoom, type GameMode } from '../api/rooms'
import { storeMasterToken } from '../api/masterToken'
import { MAX_PLAYERS, MIN_PLAYERS } from '../api/gameRules'
import BoardPanel from '../components/BoardPanel'
import RoleCard from '../components/RoleCard'
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

type Step = 'mode' | 'players' | 'roles'

const STEPS: { key: Step; label: string }[] = [
  { key: 'mode', label: 'Modalità' },
  { key: 'players', label: 'Giocatori' },
  { key: 'roles', label: 'Ruoli' },
]

function QuestSteps({ current }: { current: Step }) {
  const currentIndex = STEPS.findIndex((step) => step.key === current)

  return (
    <div className="quest-steps">
      {STEPS.map((step, index) => (
        <div
          key={step.key}
          className={
            'quest-step' +
            (index === currentIndex ? ' is-active' : index < currentIndex ? ' is-done' : '')
          }
        >
          <div className="quest-step-medallion">{index + 1}</div>
          <span className="quest-step-label">{step.label}</span>
        </div>
      ))}
    </div>
  )
}

function CreateRoomPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('mode')

  const [gameMode, setGameMode] = useState<GameMode>('CLASSIC')
  const [playerCount, setPlayerCount] = useState(8)
  const [werewolfCount, setWerewolfCount] = useState(2)
  const [priestCount, setPriestCount] = useState(1)
  const [gravediggerCount, setGravediggerCount] = useState(0)
  const [idiotCount, setIdiotCount] = useState(0)
  const [corruptedJudgeCount, setCorruptedJudgeCount] = useState(0)
  const [survivorCount, setSurvivorCount] = useState(0)

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const specialRoleCount =
    werewolfCount + priestCount + gravediggerCount + idiotCount + corruptedJudgeCount + survivorCount
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
      })
      storeMasterToken(room.code, room.masterToken)
      navigate(`/room/${room.code}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Impossibile creare la stanza. Riprova.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <BoardPanel>
      <h1>Crea partita</h1>

      <QuestSteps current={step} />

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
            <button type="button" className="button" onClick={() => setStep('mode')}>
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
              count={werewolfCount}
              min={1}
              onChange={setWerewolfCount}
            />
            <RoleCard
              icon={<PriestIcon />}
              label="Sacerdoti"
              count={priestCount}
              min={0}
              onChange={setPriestCount}
            />
            <RoleCard
              icon={<GravediggerIcon />}
              label="Becchini"
              count={gravediggerCount}
              min={0}
              onChange={setGravediggerCount}
            />
            <RoleCard
              icon={<IdiotIcon />}
              label="Idioti"
              count={idiotCount}
              min={0}
              onChange={setIdiotCount}
            />
            <RoleCard
              icon={<CorruptedJudgeIcon />}
              label="Giudice corrotto"
              count={corruptedJudgeCount}
              min={0}
              max={1}
              onChange={setCorruptedJudgeCount}
            />
            <RoleCard
              icon={<SurvivorIcon />}
              label="Sopravvissuti"
              count={survivorCount}
              min={0}
              onChange={setSurvivorCount}
            />
            <RoleCard icon={<VillagerIcon />} label="Contadini" count={villagerCount} readOnly />
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

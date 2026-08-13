import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, createRoom, type GameMode } from '../api/rooms'

const MIN_PLAYERS = 6
const MAX_PLAYERS = 30

type Step = 'mode' | 'players' | 'roles'

function CreateRoomPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('mode')

  const [gameMode, setGameMode] = useState<GameMode>('CLASSIC')
  const [playerCount, setPlayerCount] = useState(8)
  const [werewolfCount, setWerewolfCount] = useState(2)
  const [priestCount, setPriestCount] = useState(1)

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const villagerCount = Math.max(playerCount - werewolfCount - priestCount, 0)
  const rolesExceedPlayers = werewolfCount + priestCount > playerCount

  const handleCreateRoom = async () => {
    setError(null)
    setSubmitting(true)
    try {
      const room = await createRoom({
        gameMode,
        playerCount,
        werewolfCount,
        priestCount,
      })
      navigate(`/room/${room.code}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Impossibile creare la stanza. Riprova.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page">
      <h1>Crea partita</h1>

      {step === 'mode' && (
        <section className="wizard-step">
          <h2>1. Modalità di gioco</h2>
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
          <h2>2. Numero di giocatori</h2>
          <input
            type="number"
            min={MIN_PLAYERS}
            max={MAX_PLAYERS}
            value={playerCount}
            onChange={(event) => setPlayerCount(Number(event.target.value))}
          />
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
          <h2>3. Ruoli</h2>
          <label className="field">
            Lupi mannari
            <input
              type="number"
              min={1}
              value={werewolfCount}
              onChange={(event) => setWerewolfCount(Number(event.target.value))}
            />
          </label>
          <label className="field">
            Sacerdoti
            <input
              type="number"
              min={0}
              value={priestCount}
              onChange={(event) => setPriestCount(Number(event.target.value))}
            />
          </label>
          <p>Contadini: {villagerCount}</p>
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
    </div>
  )
}

export default CreateRoomPage

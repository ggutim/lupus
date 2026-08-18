import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ApiError,
  addPlayerManually,
  getRoomRoster,
  kickPlayer,
  startGame,
  type MasterRoomState,
  type Role,
} from '../api/rooms'
import { MIN_PLAYERS } from '../api/gameRules'
import BoardPanel from '../components/BoardPanel'
import PlayerTokenGrid from '../components/PlayerTokenGrid'
import { useDialog } from '../components/useDialog'
import { useMasterAccess } from '../components/useMasterAccess'
import { ROLE_LABELS } from '../roleLabels'

function RoomRosterPage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const { showAlert, showConfirm } = useDialog()
  const { masterToken, handleForbidden } = useMasterAccess(code)

  const [roster, setRoster] = useState<MasterRoomState | null>(null)
  const [nickname, setNickname] = useState('')
  const [selectedRole, setSelectedRole] = useState<Role | ''>('')
  const [adding, setAdding] = useState(false)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!code || !masterToken) return
    getRoomRoster(code, masterToken).then(setRoster).catch(handleForbidden)
  }, [code, masterToken, handleForbidden])

  if (!roster) {
    return (
      <BoardPanel>
        <h1>Giocatori</h1>
        <p>Caricamento…</p>
      </BoardPanel>
    )
  }

  const remainingForRole = (role: Role) => {
    const declared = roster.roleCounts[role] ?? 0
    const assigned = roster.players.filter((player) => player.role === role).length
    return declared - assigned
  }

  const availableRoles = (Object.keys(roster.roleCounts) as Role[]).filter((role) => remainingForRole(role) > 0)

  const handleAdd = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!code || !masterToken) return
    const trimmedNickname = nickname.trim()
    if (!trimmedNickname || (roster.manualRoles && !selectedRole)) return

    setAdding(true)
    setError(null)
    try {
      await addPlayerManually(
        code,
        masterToken,
        trimmedNickname,
        roster.manualRoles ? (selectedRole as Role) : undefined,
      )
      setNickname('')
      setSelectedRole('')
      setRoster(await getRoomRoster(code, masterToken))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Impossibile aggiungere il giocatore. Riprova.')
    } finally {
      setAdding(false)
    }
  }

  const handleRemove = async (playerId: number, playerNickname: string) => {
    if (!code || !masterToken) return

    const confirmed = await showConfirm({
      title: 'Rimuovi giocatore',
      message: `Vuoi davvero rimuovere ${playerNickname} dalla stanza?`,
      confirmLabel: 'Rimuovi',
      cancelLabel: 'Annulla',
    })
    if (!confirmed) return

    try {
      await kickPlayer(code, playerId, masterToken)
      setRoster(await getRoomRoster(code, masterToken))
    } catch {
      showAlert('Impossibile rimuovere il giocatore. Riprova.')
    }
  }

  const joinedCount = roster.players.length
  const fullyDealt = joinedCount === roster.playerCount
  const canStart = roster.manualRoles ? fullyDealt : joinedCount >= MIN_PLAYERS

  const handleStart = async () => {
    if (!code || !masterToken) return

    if (!roster.manualRoles && joinedCount < roster.playerCount) {
      const confirmed = await showConfirm({
        title: 'Iniziare comunque?',
        message: `Mancano ancora giocatori (${joinedCount}/${roster.playerCount}). Vuoi avviare la partita lo stesso?`,
        confirmLabel: 'Avvia comunque',
        cancelLabel: 'Annulla',
      })
      if (!confirmed) return
    }

    setStarting(true)
    try {
      await startGame(code, masterToken)
      navigate(`/room/${code}/game`)
    } catch {
      showAlert('Impossibile avviare la partita. Riprova.')
    } finally {
      setStarting(false)
    }
  }

  return (
    <BoardPanel>
      <h1>Giocatori</h1>

      <section className="player-list">
        <h2>
          Giocatori ({joinedCount}/{roster.playerCount})
        </h2>
        <PlayerTokenGrid
          players={roster.players}
          onRemove={handleRemove}
          emptyMessage="Nessun giocatore ancora inserito."
          showRoles={roster.manualRoles}
        />
      </section>

      {joinedCount < roster.playerCount && (
        <section className="wizard-step">
          <h2>Aggiungi giocatore</h2>
          <form onSubmit={handleAdd} className="join-form">
            <input
              type="text"
              className="nickname-input"
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              placeholder="Nome giocatore"
              maxLength={20}
            />
            {roster.manualRoles && (
              <select value={selectedRole} onChange={(event) => setSelectedRole(event.target.value as Role)}>
                <option value="">Scegli un ruolo…</option>
                {availableRoles.map((role) => (
                  <option key={role} value={role}>
                    {ROLE_LABELS[role]} ({remainingForRole(role)})
                  </option>
                ))}
              </select>
            )}
            <button
              type="submit"
              className="button button-primary"
              disabled={adding || !nickname.trim() || (roster.manualRoles && !selectedRole)}
            >
              {adding ? 'Aggiunta in corso…' : 'Aggiungi'}
            </button>
          </form>
          {error && <p className="error">{error}</p>}
        </section>
      )}

      <div className="join-form">
        <button
          type="button"
          className="button button-primary"
          disabled={!canStart || starting}
          onClick={handleStart}
        >
          {starting ? 'Avvio in corso…' : 'Inizia partita'}
        </button>

        <Link to="/" className="button">
          Torna alla home
        </Link>
      </div>
    </BoardPanel>
  )
}

export default RoomRosterPage

import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getRoomState, kickPlayer, startGame, type RoomState } from '../api/rooms'
import { MIN_PLAYERS } from '../api/gameRules'
import { subscribeToRoom } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import PlayerTokenGrid from '../components/PlayerTokenGrid'
import { useDialog } from '../components/useDialog'
import { useMasterAccess } from '../components/useMasterAccess'

function RoomCreatedPage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const [roomState, setRoomState] = useState<RoomState | null>(null)
  const [starting, setStarting] = useState(false)
  const hasAnnouncedStart = useRef(false)
  const { showAlert, showConfirm } = useDialog()
  const { masterToken, handleForbidden } = useMasterAccess(code)

  useEffect(() => {
    if (!code || !masterToken) return

    getRoomState(code, masterToken).then(setRoomState).catch(handleForbidden)

    // If the fetch above failed transiently, the live subscription still
    // populates the state once the room is reachable again.
    const unsubscribe = subscribeToRoom(code, setRoomState)
    return unsubscribe
  }, [code, masterToken, handleForbidden])

  useEffect(() => {
    if (roomState?.status === 'STARTED' && !hasAnnouncedStart.current && code) {
      hasAnnouncedStart.current = true
      showAlert({
        title: 'La partita sta per iniziare',
        message: 'Tutti i giocatori sono pronti!',
      }).then(() => navigate(`/room/${code}/game`))
    }
  }, [roomState, showAlert, code, navigate])

  const handleKick = async (playerId: number, nickname: string) => {
    if (!code || !masterToken) return

    const confirmed = await showConfirm({
      title: 'Rimuovi giocatore',
      message: `Vuoi davvero rimuovere ${nickname} dalla stanza?`,
      confirmLabel: 'Rimuovi',
      cancelLabel: 'Annulla',
    })
    if (!confirmed) return

    try {
      await kickPlayer(code, playerId, masterToken)
    } catch {
      showAlert('Impossibile rimuovere il giocatore. Riprova.')
    }
  }

  const joinedCount = roomState?.players.length ?? 0
  const totalCount = roomState?.playerCount ?? 0
  const roomIsFull = totalCount > 0 && joinedCount >= totalCount
  const canStart = joinedCount >= MIN_PLAYERS

  const handleStartGame = async () => {
    if (!code || !masterToken) return

    if (!roomIsFull) {
      const confirmed = await showConfirm({
        title: 'Iniziare comunque?',
        message: `Mancano ancora giocatori (${joinedCount}/${totalCount}). Vuoi avviare la partita lo stesso?`,
        confirmLabel: 'Avvia comunque',
        cancelLabel: 'Annulla',
      })
      if (!confirmed) return
    }

    setStarting(true)
    try {
      await startGame(code, masterToken)
    } catch {
      showAlert('Impossibile avviare la partita. Riprova.')
    } finally {
      setStarting(false)
    }
  }

  return (
    <BoardPanel>
      <h1>Partita creata</h1>

      <div className="code-plaque-wrapper">
        <div className="code-plaque">
          <span className="code-plaque-code">{code}</span>
        </div>
        <p className="code-plaque-hint">Condividi questo codice con i giocatori</p>
      </div>

      <section className="player-list">
        <h2>
          Giocatori ({joinedCount}/{totalCount})
        </h2>
        <PlayerTokenGrid
          players={roomState?.players ?? []}
          onRemove={handleKick}
          emptyMessage="Nessun giocatore è ancora entrato."
        />
      </section>

      <div className="join-form">
        {roomState?.status !== 'STARTED' && (
          <button
            type="button"
            className="button button-primary"
            onClick={handleStartGame}
            disabled={!canStart || starting}
          >
            {starting ? 'Avvio in corso…' : 'Inizia partita'}
          </button>
        )}

        <Link to="/" className="button">
          Torna alla home
        </Link>
      </div>
    </BoardPanel>
  )
}

export default RoomCreatedPage

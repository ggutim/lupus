import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getRoomState, kickPlayer, type RoomState } from '../api/rooms'
import { subscribeToRoom } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import { useDialog } from '../components/useDialog'
import { MeepleIcon } from '../components/icons'

function RoomCreatedPage() {
  const { code } = useParams<{ code: string }>()
  const [roomState, setRoomState] = useState<RoomState | null>(null)
  const hasAnnouncedStart = useRef(false)
  const { showAlert, showConfirm } = useDialog()

  useEffect(() => {
    if (!code) return

    getRoomState(code)
      .then(setRoomState)
      .catch(() => {
        // The live subscription below will still populate the state once
        // the room is reachable again; nothing else to do here for now.
      })

    const unsubscribe = subscribeToRoom(code, (state) => {
      setRoomState(state)
    })

    return unsubscribe
  }, [code])

  useEffect(() => {
    if (roomState?.status === 'STARTED' && !hasAnnouncedStart.current) {
      hasAnnouncedStart.current = true
      showAlert({
        title: 'La partita sta per iniziare',
        message: 'Tutti i giocatori sono pronti!',
      })
    }
  }, [roomState, showAlert])

  const handleKick = async (playerId: number, nickname: string) => {
    if (!code) return

    const confirmed = await showConfirm({
      title: 'Rimuovi giocatore',
      message: `Vuoi davvero rimuovere ${nickname} dalla stanza?`,
      confirmLabel: 'Rimuovi',
      cancelLabel: 'Annulla',
    })
    if (!confirmed) return

    try {
      await kickPlayer(code, playerId)
    } catch {
      showAlert('Impossibile rimuovere il giocatore. Riprova.')
    }
  }

  const joinedCount = roomState?.players.length ?? 0
  const totalCount = roomState?.playerCount ?? 0

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
        {joinedCount === 0 ? (
          <p className="player-list-empty">Nessun giocatore è ancora entrato.</p>
        ) : (
          <div className="player-tokens">
            {roomState?.players.map((player) => (
              <div className="player-token" key={player.id}>
                <div className="player-token-meeple-wrapper">
                  <div className="player-token-meeple">
                    <MeepleIcon />
                  </div>
                  <button
                    type="button"
                    className="player-token-kick"
                    onClick={() => handleKick(player.id, player.nickname)}
                    aria-label={`Rimuovi ${player.nickname}`}
                    title={`Rimuovi ${player.nickname}`}
                  >
                    ×
                  </button>
                </div>
                <span className="player-token-name">{player.nickname}</span>
              </div>
            ))}
          </div>
        )}
      </section>

      <Link to="/" className="button">
        Torna alla home
      </Link>
    </BoardPanel>
  )
}

export default RoomCreatedPage

import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getRoomState, type RoomState } from '../api/rooms'
import { subscribeToRoom } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import { MeepleIcon } from '../components/icons'

function RoomCreatedPage() {
  const { code } = useParams<{ code: string }>()
  const [roomState, setRoomState] = useState<RoomState | null>(null)
  const hasAnnouncedStart = useRef(false)

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
      alert('Tutti i giocatori sono pronti! La partita sta per iniziare.')
    }
  }, [roomState])

  const joinedCount = roomState?.players.length ?? 0
  const totalCount = roomState?.playerCount ?? 0

  return (
    <BoardPanel>
      <h1>Partita creata</h1>

      <div className="wax-seal-wrapper">
        <div className="wax-seal">
          <span className="wax-seal-code">{code}</span>
        </div>
        <p className="wax-seal-hint">Condividi questo codice con i giocatori</p>
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
              <div className="player-token" key={player.nickname}>
                <div className="player-token-meeple">
                  <MeepleIcon />
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

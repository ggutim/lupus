import { useEffect, useRef } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { subscribeToRoom } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import { useDialog } from '../components/useDialog'

function PlayerWaitingPage() {
  const { code } = useParams<{ code: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const nickname = (location.state as { nickname?: string } | null)?.nickname
  const { showAlert } = useDialog()
  const hasAnnouncedStart = useRef(false)

  useEffect(() => {
    if (!code || !nickname) return

    const unsubscribe = subscribeToRoom(code, (state) => {
      if (state.status === 'STARTED') {
        if (hasAnnouncedStart.current) return
        hasAnnouncedStart.current = true
        showAlert({
          title: 'La partita sta per iniziare',
          message: 'Controlla il tuo ruolo!',
        }).then(() => navigate(`/room/${code}/role`, { state: { nickname } }))
        return
      }

      const stillInRoom = state.players.some((player) => player.nickname === nickname)
      if (!stillInRoom) {
        showAlert({
          title: 'Sei stato rimosso',
          message: 'Il narratore ti ha rimosso dalla stanza.',
        }).then(() => navigate('/'))
      }
    })

    return unsubscribe
  }, [code, nickname, navigate, showAlert])

  return (
    <BoardPanel>
      <h1>In attesa...</h1>
      {nickname && <p>Ciao {nickname}!</p>}
      <p>In attesa che il narratore avvii la partita.</p>

      <div className="code-plaque-wrapper">
        <div className="code-plaque">
          <span className="code-plaque-code">{code}</span>
        </div>
      </div>
    </BoardPanel>
  )
}

export default PlayerWaitingPage

import { useEffect } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { subscribeToRoom } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'

function PlayerWaitingPage() {
  const { code } = useParams<{ code: string }>()
  const location = useLocation()
  const nickname = (location.state as { nickname?: string } | null)?.nickname

  useEffect(() => {
    if (!code) return

    const unsubscribe = subscribeToRoom(code, (state) => {
      if (state.status === 'STARTED') {
        alert('La partita sta per iniziare!')
      }
    })

    return unsubscribe
  }, [code])

  return (
    <BoardPanel>
      <h1>In attesa...</h1>
      {nickname && <p>Ciao {nickname}!</p>}
      <p>In attesa che il narratore avvii la partita.</p>

      <div className="wax-seal-wrapper">
        <div className="wax-seal">
          <span className="wax-seal-code">{code}</span>
        </div>
      </div>
    </BoardPanel>
  )
}

export default PlayerWaitingPage

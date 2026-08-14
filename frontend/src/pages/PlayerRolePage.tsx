import { useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, getPlayerRole, type Role } from '../api/rooms'
import { getPlayerToken } from '../api/playerToken'
import BoardPanel from '../components/BoardPanel'
import { useDialog } from '../components/useDialog'
import { PriestIcon, VillagerIcon, WerewolfIcon } from '../components/icons'

const ROLE_INFO: Record<Role, { label: string; description: string; icon: ReactNode }> = {
  WEREWOLF: {
    label: 'Lupo mannaro',
    description: 'Ogni notte, insieme agli altri lupi, scegli in silenzio chi sbranare.',
    icon: <WerewolfIcon />,
  },
  PRIEST: {
    label: 'Sacerdote',
    description: 'Ogni notte puoi scoprire se un giocatore è buono o malvagio.',
    icon: <PriestIcon />,
  },
  VILLAGER: {
    label: 'Contadino',
    description: 'Non hai poteri speciali: osserva, discuti e vota con attenzione.',
    icon: <VillagerIcon />,
  },
}

function PlayerRolePage() {
  const { code } = useParams<{ code: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const nickname = (location.state as { nickname?: string } | null)?.nickname
  const { showAlert } = useDialog()
  const [role, setRole] = useState<Role | null>(null)

  useEffect(() => {
    if (!code) return

    const stored = getPlayerToken(code)
    if (!stored) {
      showAlert({
        title: 'Ruolo non disponibile',
        message: 'Non risulti aver effettuato l\'accesso a questa stanza su questo dispositivo.',
      }).then(() => navigate('/'))
      return
    }

    getPlayerRole(code, stored.playerId, stored.token)
      .then(setRole)
      .catch((err) => {
        if (err instanceof ApiError) {
          showAlert({
            title: 'Impossibile recuperare il ruolo',
            message: err.message,
          }).then(() => navigate('/'))
        }
      })
  }, [code, navigate, showAlert])

  if (!role) {
    return (
      <BoardPanel>
        <h1>Il tuo ruolo</h1>
        <p>Caricamento…</p>
      </BoardPanel>
    )
  }

  const info = ROLE_INFO[role]

  return (
    <BoardPanel>
      <h1>Il tuo ruolo</h1>
      {nickname && <p>Ciao {nickname}!</p>}

      <div className="role-reveal">
        <div className="role-reveal-icon">{info.icon}</div>
        <h2 className="role-reveal-label">{info.label}</h2>
        <p className="role-reveal-description">{info.description}</p>
      </div>

      <p className="role-reveal-hint">Non mostrare lo schermo agli altri giocatori.</p>
    </BoardPanel>
  )
}

export default PlayerRolePage

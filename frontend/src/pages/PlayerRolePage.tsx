import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, getPlayerRole, getVillageOverview, type PlayerRoleState, type Role, type VillagePlayer } from '../api/rooms'
import { getPlayerToken } from '../api/playerToken'
import { subscribeToGame } from '../api/roomSocket'
import BoardPanel from '../components/BoardPanel'
import VillageOverviewDialog from '../components/VillageOverviewDialog'
import { useDialog } from '../components/useDialog'
import {
  AngelIcon,
  CorruptedJudgeIcon,
  GhostIcon,
  GravediggerIcon,
  IdiotIcon,
  MeepleIcon,
  PriestIcon,
  SkullIcon,
  SurvivorIcon,
  VillagerIcon,
  WerewolfIcon,
} from '../components/icons'
import { ROLE_ALIGNMENT } from '../roleAlignment'
import { ROLE_LABELS } from '../roleLabels'

const ROLE_INFO: Record<Role, { description: string; icon: ReactNode }> = {
  WEREWOLF: {
    description: 'Ogni notte, insieme agli altri lupi, scegli in silenzio chi sbranare.',
    icon: <WerewolfIcon />,
  },
  PRIEST: {
    description: 'Ogni notte puoi scoprire se un giocatore è buono o malvagio.',
    icon: <PriestIcon />,
  },
  GRAVEDIGGER: {
    description: 'Ogni notte, se c\'è almeno un morto, puoi scoprire se era buono o malvagio.',
    icon: <GravediggerIcon />,
  },
  IDIOT: {
    description: 'Non hai poteri e giochi per te stesso. Se il villaggio ti elimina con il voto, vinci da solo!',
    icon: <IdiotIcon />,
  },
  CORRUPTED_JUDGE: {
    description: 'Se durante il giorno nessuno viene eliminato dal voto, la notte seguente puoi eliminare chi vuoi, anche un lupo.',
    icon: <CorruptedJudgeIcon />,
  },
  SURVIVOR: {
    description: 'Sei un contadino come gli altri, ma hai una vita in più: se i lupi mannari ti sbranano, la prima volta sopravvivi.',
    icon: <SurvivorIcon />,
  },
  VILLAGER: {
    description: 'Non hai poteri speciali: osserva, discuti e vota con attenzione.',
    icon: <VillagerIcon />,
  },
  GHOST: {
    description:
      'Sei un fantasma: ogni notte, insieme agli altri fantasmi, scegliete in silenzio due giocatori da maledire.',
    icon: <GhostIcon />,
  },
  ANGEL: {
    description:
      'Sei un angelo: ogni notte, insieme agli altri angeli, scegliete in silenzio un giocatore da proteggere dai lupi mannari.',
    icon: <AngelIcon />,
  },
}

function PlayerRolePage() {
  const { code } = useParams<{ code: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const nickname = (location.state as { nickname?: string } | null)?.nickname
  const { showAlert } = useDialog()
  const [status, setStatus] = useState<PlayerRoleState | null>(null)
  const [village, setVillage] = useState<VillagePlayer[]>([])
  const [villageOpen, setVillageOpen] = useState(false)
  const [revealed, setRevealed] = useState(false)

  const refresh = useCallback(() => {
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
      .then(setStatus)
      .catch((err) => {
        if (err instanceof ApiError) {
          showAlert({
            title: 'Impossibile recuperare il ruolo',
            message: err.message,
          }).then(() => navigate('/'))
        }
      })

    getVillageOverview(code).then(setVillage).catch(() => {})
  }, [code, navigate, showAlert])

  useEffect(() => {
    if (!code) return

    refresh()
    const unsubscribe = subscribeToGame(code, refresh)
    return unsubscribe
  }, [code, refresh])

  if (!status) {
    return (
      <BoardPanel>
        <h1>Il tuo ruolo</h1>
        <p>Caricamento…</p>
      </BoardPanel>
    )
  }

  const villageButton = (
    <button type="button" className="button" onClick={() => setVillageOpen(true)}>
      Villaggio
    </button>
  )

  if (!status.alive) {
    return (
      <BoardPanel>
        <h1>Sei stato eliminato</h1>
        {nickname && <p>Mi dispiace, {nickname}.</p>}

        <div className="role-reveal role-reveal-dead">
          <div className="role-reveal-icon">
            <SkullIcon />
          </div>
          <h2 className="role-reveal-label">Sei morto!</h2>
          <p className="role-reveal-description">Resta in silenzio e osserva come andrà a finire la partita.</p>
        </div>

        {villageButton}
        <VillageOverviewDialog open={villageOpen} onClose={() => setVillageOpen(false)} players={village} />
      </BoardPanel>
    )
  }

  const info = ROLE_INFO[status.role]

  return (
    <BoardPanel>
      <h1>Il tuo ruolo</h1>
      {nickname && <p>Ciao {nickname}!</p>}

      <button
        type="button"
        className="role-reveal-toggle"
        onClick={() => setRevealed((prev) => !prev)}
        aria-pressed={revealed}
      >
        {revealed ? (
          <div className="role-reveal">
            <div className={'role-reveal-icon align-' + ROLE_ALIGNMENT[status.role].toLowerCase()}>{info.icon}</div>
            <h2 className="role-reveal-label">{ROLE_LABELS[status.role]}</h2>
            <p className="role-reveal-description">{info.description}</p>
          </div>
        ) : (
          <div className="role-reveal role-reveal-facedown">
            <div className="role-reveal-icon">
              <MeepleIcon />
            </div>
            <h2 className="role-reveal-label">Ruolo nascosto</h2>
          </div>
        )}
      </button>

      {revealed ? (
        <>
          <p className="role-reveal-hint">Tocca di nuovo per nascondere il ruolo.</p>
          <p className="role-reveal-hint">Non mostrare lo schermo agli altri giocatori.</p>
        </>
      ) : (
        <p className="role-reveal-hint">Tocca per scoprire il tuo ruolo.</p>
      )}

      {villageButton}
      <VillageOverviewDialog open={villageOpen} onClose={() => setVillageOpen(false)} players={village} />
    </BoardPanel>
  )
}

export default PlayerRolePage

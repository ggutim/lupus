import type { Role } from '../api/rooms'
import { ROLE_LABELS } from '../roleLabels'
import { MeepleIcon } from './icons'

export interface PlayerToken {
  id: number
  nickname: string
  role?: Role | null
}

interface PlayerTokenGridProps {
  players: PlayerToken[]
  onRemove: (playerId: number, nickname: string) => void
  emptyMessage: string
  /** Shows each player's role label under their name, when available. */
  showRoles?: boolean
}

/** The roster grid shared by every pre-game player-management screen: a meeple, a kick button, and a name. */
function PlayerTokenGrid({ players, onRemove, emptyMessage, showRoles }: PlayerTokenGridProps) {
  if (players.length === 0) {
    return <p className="player-list-empty">{emptyMessage}</p>
  }

  return (
    <div className="player-tokens">
      {players.map((player) => (
        <div className="player-token" key={player.id}>
          <div className="player-token-meeple-wrapper">
            <div className="player-token-meeple">
              <MeepleIcon />
            </div>
            <button
              type="button"
              className="player-token-kick"
              onClick={() => onRemove(player.id, player.nickname)}
              aria-label={`Rimuovi ${player.nickname}`}
              title={`Rimuovi ${player.nickname}`}
            >
              ×
            </button>
          </div>
          <span className="player-token-name">{player.nickname}</span>
          {showRoles && player.role && <span className="player-token-role">{ROLE_LABELS[player.role]}</span>}
        </div>
      ))}
    </div>
  )
}

export default PlayerTokenGrid

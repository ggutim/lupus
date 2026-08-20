import type { Role } from '../api/rooms'
import { ROLE_ALIGNMENT } from '../roleAlignment'
import { ROLE_LABELS } from '../roleLabels'

export interface VillageOverviewPlayer {
  id: number
  nickname: string
  alive: boolean
  role?: Role
}

interface VillageOverviewDialogProps {
  open: boolean
  onClose: () => void
  players: VillageOverviewPlayer[]
}

/**
 * Read-only roster overlay: nickname and alive/dead status for everyone
 * in the room, plus each player's role when the caller (the master)
 * passes one. Used as-is by both the master and player screens — only
 * the data fed in differs.
 */
function VillageOverviewDialog({ open, onClose, players }: VillageOverviewDialogProps) {
  if (!open) return null

  return (
    <div className="dialog-overlay" role="presentation" onClick={onClose}>
      <div
        className="dialog-box village-overview-box"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className="dialog-title">Il villaggio</h2>

        <ul className="village-overview-list">
          {players.map((player) => (
            <li
              key={player.id}
              className={
                'village-overview-row' +
                (player.role ? ' align-' + ROLE_ALIGNMENT[player.role].toLowerCase() : '') +
                (player.alive ? '' : ' is-dead')
              }
            >
              <span className="village-overview-name">{player.nickname}</span>
              <span className="village-overview-meta">
                {player.role && <span className="village-overview-role">{ROLE_LABELS[player.role]}</span>}
                <span className="village-overview-status">{player.alive ? 'Vivo' : 'Morto'}</span>
              </span>
            </li>
          ))}
        </ul>

        <button type="button" className="button button-primary" onClick={onClose}>
          Chiudi
        </button>
      </div>
    </div>
  )
}

export default VillageOverviewDialog

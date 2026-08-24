import { useState } from 'react'
import type { Role } from '../api/rooms'
import { ROLE_LABELS } from '../roleLabels'
import { ROLE_SETUP_INFO } from '../roleSetupInfo'

interface KillerGuessTarget {
  id: number
  nickname: string
}

interface KillerGuessDialogProps {
  open: boolean
  onClose: () => void
  /** Alive players the killer could guess — the killer himself is already excluded by the caller. */
  targets: KillerGuessTarget[]
  busy: boolean
  onConfirm: (targetPlayerId: number, guessedRole: Role) => void
}

const GUESSABLE_ROLES = Object.keys(ROLE_SETUP_INFO) as Role[]

/**
 * The killer's once-per-game power: reveal himself and guess another
 * player's exact role, in one shot — pick target and role, confirm.
 * Built on the same `.dialog-overlay`/`.dialog-box` shell as every
 * other popup, but its own form rather than reusing `InfoDialog`
 * (read-only) or the game screen's selection grid (single-value, no
 * accompanying role picker).
 */
function KillerGuessDialog({ open, onClose, targets, busy, onConfirm }: KillerGuessDialogProps) {
  const [targetPlayerId, setTargetPlayerId] = useState('')
  const [guessedRole, setGuessedRole] = useState('')

  if (!open) return null

  const canConfirm = targetPlayerId !== '' && guessedRole !== '' && !busy

  const handleConfirm = () => {
    if (!canConfirm) return
    onConfirm(Number(targetPlayerId), guessedRole as Role)
  }

  return (
    <div className="dialog-overlay" role="presentation" onClick={onClose}>
      <div
        className="dialog-box"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className="dialog-title">Il killer si rivela</h2>
        <p className="dialog-message">
          Scegli chi ha scelto di smascherare e il ruolo che indovina. Se ha ragione, quel giocatore muore. Se ha
          torto, muore lui.
        </p>
        <select value={targetPlayerId} onChange={(event) => setTargetPlayerId(event.target.value)}>
          <option value="">Chi indovina…</option>
          {targets.map((target) => (
            <option key={target.id} value={target.id}>
              {target.nickname}
            </option>
          ))}
        </select>
        <select value={guessedRole} onChange={(event) => setGuessedRole(event.target.value)}>
          <option value="">Ruolo indovinato…</option>
          {GUESSABLE_ROLES.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role]}
            </option>
          ))}
        </select>
        <div className="dialog-actions">
          <button type="button" className="button" onClick={onClose} disabled={busy}>
            Annulla
          </button>
          <button type="button" className="button button-primary" onClick={handleConfirm} disabled={!canConfirm}>
            {busy ? 'Attendere…' : 'Conferma'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default KillerGuessDialog

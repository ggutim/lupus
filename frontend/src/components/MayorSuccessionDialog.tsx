import { useState } from 'react'

interface MayorSuccessionTarget {
  id: number
  nickname: string
}

interface MayorSuccessionDialogProps {
  open: boolean
  deadMayorName: string
  targets: MayorSuccessionTarget[]
  busy: boolean
  onConfirm: (successorPlayerId: number) => void
}

/**
 * Mandatory prompt once the current mayor dies and at least one other
 * player is alive to inherit the card — no cancel or close: the
 * backend refuses every other action until this resolves, so the
 * dialog can't be dismissed either.
 */
function MayorSuccessionDialog({ open, deadMayorName, targets, busy, onConfirm }: MayorSuccessionDialogProps) {
  const [successorPlayerId, setSuccessorPlayerId] = useState('')

  if (!open) return null

  const canConfirm = successorPlayerId !== '' && !busy

  const handleConfirm = () => {
    if (!canConfirm) return
    onConfirm(Number(successorPlayerId))
  }

  return (
    <div className="dialog-overlay" role="presentation">
      <div className="dialog-box" role="dialog" aria-modal="true">
        <h2 className="dialog-title">Il sindaco ha scelto un erede</h2>
        <p className="dialog-message">{deadMayorName} è morto/a: a chi ha lasciato la carica di sindaco?</p>
        <select value={successorPlayerId} onChange={(event) => setSuccessorPlayerId(event.target.value)}>
          <option value="">Scegli il nuovo sindaco…</option>
          {targets.map((target) => (
            <option key={target.id} value={target.id}>
              {target.nickname}
            </option>
          ))}
        </select>
        <div className="dialog-actions">
          <button type="button" className="button button-primary" onClick={handleConfirm} disabled={!canConfirm}>
            {busy ? 'Attendere…' : 'Conferma'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default MayorSuccessionDialog

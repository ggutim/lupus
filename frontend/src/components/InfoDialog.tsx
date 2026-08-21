import type { ReactNode } from 'react'
import type { RoleAlignment } from '../roleAlignment'

interface InfoDialogProps {
  open: boolean
  onClose: () => void
  icon: ReactNode
  /** Colors the icon and shows a "Buono"/"Malvagio" tag; omitted for a dialog that isn't about a role (e.g. a game mode). */
  align?: RoleAlignment
  title: string
  children: ReactNode
}

/**
 * Read-only explanatory popup: an icon, a title, and free-form body
 * content (paragraphs, lists). Reused for both a role's rules (room
 * creation) and a game mode's rules — built on the same `.dialog-overlay`/
 * `.dialog-box` shell as every other popup in the app.
 */
function InfoDialog({ open, onClose, icon, align, title, children }: InfoDialogProps) {
  if (!open) return null

  return (
    <div className="dialog-overlay" role="presentation" onClick={onClose}>
      <div
        className="dialog-box info-dialog-box"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <div className={'info-dialog-icon' + (align ? ' align-' + align.toLowerCase() : '')}>{icon}</div>
        <h2 className="dialog-title">{title}</h2>
        {align && (
          <span className={'dialog-align-tag align-' + align.toLowerCase()}>
            {align === 'EVIL' ? 'Malvagio' : 'Buono'}
          </span>
        )}
        <div className="info-dialog-body">{children}</div>
        <button type="button" className="button button-primary" onClick={onClose}>
          Chiudi
        </button>
      </div>
    </div>
  )
}

export default InfoDialog

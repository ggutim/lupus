export interface SpecialPowerAction {
  key: string
  label: string
  onSelect: () => void
}

interface SpecialPowersDialogProps {
  open: boolean
  onClose: () => void
  actions: SpecialPowerAction[]
}

/**
 * Single entry point for whichever voluntary day-time reveals are
 * currently available (the killer's, the mayor's, and any future
 * ones) — picking one closes this menu and hands off to that power's
 * own flow. Keeps the game screen from sprouting a growing stack of
 * small reveal links as more roles gain one.
 */
function SpecialPowersDialog({ open, onClose, actions }: SpecialPowersDialogProps) {
  if (!open) return null

  return (
    <div className="dialog-overlay" role="presentation" onClick={onClose}>
      <div
        className="dialog-box"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className="dialog-title">Poteri speciali</h2>
        <div className="special-powers-list">
          {actions.map((action) => (
            <button
              key={action.key}
              type="button"
              className="special-powers-action"
              onClick={() => {
                onClose()
                action.onSelect()
              }}
            >
              {action.label}
            </button>
          ))}
        </div>
        <button type="button" className="button" onClick={onClose}>
          Annulla
        </button>
      </div>
    </div>
  )
}

export default SpecialPowersDialog

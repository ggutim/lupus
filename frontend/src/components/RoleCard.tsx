import type { ReactNode } from 'react'
import type { RoleAlignment } from '../roleAlignment'

interface RoleCardProps {
  icon: ReactNode
  label: string
  count: number
  /** Colors the icon by which side the role plays for; omitted for a card that isn't a role (e.g. player count). */
  align?: RoleAlignment
  min?: number
  max?: number
  onChange?: (count: number) => void
  readOnly?: boolean
}

/**
 * A single role's selection card: icon, name, and a +/- stepper (or a
 * read-only count, for roles like the villager whose number is derived).
 */
function RoleCard({ icon, label, count, align, min = 0, max = 99, onChange, readOnly = false }: RoleCardProps) {
  return (
    <div className={'role-card' + (readOnly ? ' is-readonly' : '')}>
      <div className={'role-card-icon' + (align ? ' align-' + align.toLowerCase() : '')}>{icon}</div>
      <div className="role-card-label">{label}</div>
      {readOnly ? (
        <div className="role-card-count">{count}</div>
      ) : (
        <div className="role-card-stepper">
          <button
            type="button"
            className="role-card-step-btn"
            onClick={() => onChange?.(Math.max(min, count - 1))}
            disabled={count <= min}
            aria-label={`Diminuisci ${label}`}
          >
            −
          </button>
          <span className="role-card-count">{count}</span>
          <button
            type="button"
            className="role-card-step-btn"
            onClick={() => onChange?.(Math.min(max, count + 1))}
            disabled={count >= max}
            aria-label={`Aumenta ${label}`}
          >
            +
          </button>
        </div>
      )}
    </div>
  )
}

export default RoleCard

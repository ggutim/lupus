import type { ReactNode } from 'react'

interface RoleCardProps {
  icon: ReactNode
  label: string
  count: number
  min?: number
  max?: number
  onChange?: (count: number) => void
  readOnly?: boolean
}

/**
 * A single role's selection card: icon, name, and a +/- stepper (or a
 * read-only count, for roles like the villager whose number is derived).
 */
function RoleCard({ icon, label, count, min = 0, max = 99, onChange, readOnly = false }: RoleCardProps) {
  return (
    <div className={'role-card' + (readOnly ? ' is-readonly' : '')}>
      <div className="role-card-icon">{icon}</div>
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

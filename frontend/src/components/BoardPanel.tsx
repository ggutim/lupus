import type { ReactNode } from 'react'

interface BoardPanelProps {
  children: ReactNode
  className?: string
}

/**
 * The main content frame used across all screens: a thick, beveled
 * game-box panel with corner brackets, evoking a board or box lid
 * sitting on a table rather than a parchment scroll.
 */
function BoardPanel({ children, className }: BoardPanelProps) {
  return (
    <div className={'board-panel' + (className ? ` ${className}` : '')}>
      <span className="board-panel-corner board-panel-corner-tl" />
      <span className="board-panel-corner board-panel-corner-tr" />
      <span className="board-panel-corner board-panel-corner-bl" />
      <span className="board-panel-corner board-panel-corner-br" />
      <div className="board-panel-content">{children}</div>
    </div>
  )
}

export default BoardPanel

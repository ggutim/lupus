import { Link } from 'react-router-dom'
import BoardPanel from '../components/BoardPanel'

function LandingPage() {
  return (
    <BoardPanel>
      <h1>Lupus</h1>
      <div className="landing-actions">
        <Link to="/create" className="button button-primary">
          Crea partita
        </Link>
        <Link to="/join" className="button">
          Unisciti a una partita
        </Link>
      </div>
    </BoardPanel>
  )
}

export default LandingPage

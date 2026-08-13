import { Link } from 'react-router-dom'
import BoardPanel from '../components/BoardPanel'

function LandingPage() {
  return (
    <BoardPanel>
      <h1>Lupus in Tabula</h1>
      <p className="landing-subtitle">Il villaggio dorme... ma il male è già tra voi.</p>
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

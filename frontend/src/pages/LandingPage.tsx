import { Link } from 'react-router-dom'

function LandingPage() {
  return (
    <div className="landing">
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
    </div>
  )
}

export default LandingPage

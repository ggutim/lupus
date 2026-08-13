import { Link, useParams } from 'react-router-dom'

function RoomCreatedPage() {
  const { code } = useParams<{ code: string }>()

  return (
    <div className="page">
      <h1>Partita creata</h1>
      <p>Condividi questo codice con i giocatori:</p>
      <p className="room-code">{code}</p>
      <Link to="/" className="button">
        Torna alla home
      </Link>
    </div>
  )
}

export default RoomCreatedPage

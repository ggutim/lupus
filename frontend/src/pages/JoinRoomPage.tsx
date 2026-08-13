import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, joinRoom } from '../api/rooms'
import BoardPanel from '../components/BoardPanel'

function JoinRoomPage() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [nickname, setNickname] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await joinRoom(code.trim().toUpperCase(), nickname.trim())
      navigate(`/room/${code.trim().toUpperCase()}/waiting`, { state: { nickname: nickname.trim() } })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Impossibile unirsi alla stanza. Riprova.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <BoardPanel>
      <h1>Unisciti a una partita</h1>
      <form onSubmit={handleSubmit} className="join-form">
        <input
          type="text"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          placeholder="Codice stanza"
          maxLength={4}
          autoFocus
        />
        <input
          type="text"
          className="nickname-input"
          value={nickname}
          onChange={(event) => setNickname(event.target.value)}
          placeholder="Il tuo nome"
          maxLength={20}
        />
        <button type="submit" className="button button-primary" disabled={!code || !nickname || submitting}>
          {submitting ? 'Ingresso in corso…' : 'Entra'}
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      <Link to="/" className="button">
        Torna indietro
      </Link>
    </BoardPanel>
  )
}

export default JoinRoomPage

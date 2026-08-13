import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

function JoinRoomPage() {
  const [code, setCode] = useState('')
  const [submittedCode, setSubmittedCode] = useState<string | null>(null)

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmittedCode(code)
  }

  return (
    <div className="page">
      <h1>Unisciti a una partita</h1>
      <form onSubmit={handleSubmit} className="join-form">
        <input
          type="text"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          placeholder="Codice stanza"
          maxLength={6}
          autoFocus
        />
        <button type="submit" className="button button-primary" disabled={!code}>
          Entra
        </button>
      </form>
      {submittedCode && (
        <p>L'ingresso nella stanza "{submittedCode}" sarà disponibile a breve.</p>
      )}
      <Link to="/" className="button">
        Torna indietro
      </Link>
    </div>
  )
}

export default JoinRoomPage

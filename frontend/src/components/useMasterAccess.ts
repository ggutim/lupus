import { useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/rooms'
import { getMasterToken } from '../api/masterToken'
import { useDialog } from './useDialog'

/**
 * Shared guard for every master-only page: reads the room's stored master
 * token and redirects home with an explanatory alert if it's missing, and
 * hands back a `handleForbidden` catch-helper for the same redirect when
 * the backend itself rejects the token.
 */
export function useMasterAccess(code: string | undefined) {
  const navigate = useNavigate()
  const { showAlert } = useDialog()
  const masterToken = code ? getMasterToken(code) : null

  useEffect(() => {
    if (!code || masterToken) return
    showAlert({
      title: 'Accesso non disponibile',
      message: 'Non risulti essere il narratore di questa stanza su questo dispositivo.',
    }).then(() => navigate('/'))
  }, [code, masterToken, navigate, showAlert])

  const handleForbidden = useCallback(
    (err: unknown) => {
      if (err instanceof ApiError && err.status === 403) {
        showAlert({
          title: 'Accesso negato',
          message: 'Non risulti essere il narratore di questa stanza.',
        }).then(() => navigate('/'))
      }
    },
    [navigate, showAlert],
  )

  return { masterToken, handleForbidden }
}

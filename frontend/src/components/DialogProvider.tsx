import { useCallback, useRef, useState, type ReactNode } from 'react'
import { DialogContext, type DialogOptions } from './useDialog'

interface DialogState extends DialogOptions {
  kind: 'alert' | 'confirm'
}

interface QueuedDialog extends DialogState {
  resolve: (value: boolean) => void
}

function normalizeOptions(options: DialogOptions | string): DialogOptions {
  return typeof options === 'string' ? { message: options } : options
}

/**
 * Only one dialog can be visible at a time, but callers don't
 * coordinate that — e.g. two rapid clicks on different "Rimuovi"
 * buttons each call `showConfirm`. Without a queue, the second call
 * would silently replace the first dialog's content out from under
 * the user and leave the first call's promise unresolved forever
 * (its `finally`, e.g. clearing a `busy` flag, would never run).
 * Instead, a request made while one is already showing waits in
 * `queueRef` and is shown the moment the current one closes.
 */
export function DialogProvider({ children }: { children: ReactNode }) {
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const resolveRef = useRef<((value: boolean) => void) | null>(null)
  const queueRef = useRef<QueuedDialog[]>([])

  const showNext = useCallback(() => {
    const next = queueRef.current.shift()
    if (!next) {
      resolveRef.current = null
      setDialog(null)
      return
    }
    const { resolve, ...state } = next
    resolveRef.current = resolve
    setDialog(state)
  }, [])

  const close = useCallback(
    (result: boolean) => {
      resolveRef.current?.(result)
      showNext()
    },
    [showNext],
  )

  const enqueue = useCallback((entry: QueuedDialog) => {
    if (resolveRef.current === null && queueRef.current.length === 0) {
      const { resolve, ...state } = entry
      resolveRef.current = resolve
      setDialog(state)
    } else {
      queueRef.current.push(entry)
    }
  }, [])

  const showAlert = useCallback(
    (options: DialogOptions | string) => {
      return new Promise<void>((resolve) => {
        enqueue({ kind: 'alert', ...normalizeOptions(options), resolve: () => resolve() })
      })
    },
    [enqueue],
  )

  const showConfirm = useCallback(
    (options: DialogOptions | string) => {
      return new Promise<boolean>((resolve) => {
        enqueue({ kind: 'confirm', ...normalizeOptions(options), resolve })
      })
    },
    [enqueue],
  )

  return (
    <DialogContext.Provider value={{ showAlert, showConfirm }}>
      {children}
      {dialog && (
        <div className="dialog-overlay" role="presentation" onClick={() => close(false)}>
          <div
            className="dialog-box"
            role={dialog.kind === 'confirm' ? 'alertdialog' : 'alert'}
            aria-modal="true"
            onClick={(event) => event.stopPropagation()}
          >
            {dialog.title && <h2 className="dialog-title">{dialog.title}</h2>}
            <p className="dialog-message">{dialog.message}</p>
            <div className="dialog-actions">
              {dialog.kind === 'confirm' && (
                <button type="button" className="button" onClick={() => close(false)}>
                  {dialog.cancelLabel ?? 'Annulla'}
                </button>
              )}
              <button type="button" className="button button-primary" onClick={() => close(true)} autoFocus>
                {dialog.confirmLabel ?? 'Va bene'}
              </button>
            </div>
          </div>
        </div>
      )}
    </DialogContext.Provider>
  )
}

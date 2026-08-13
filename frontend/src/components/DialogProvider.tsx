import { useCallback, useRef, useState, type ReactNode } from 'react'
import { DialogContext, type DialogOptions } from './useDialog'

interface DialogState extends DialogOptions {
  kind: 'alert' | 'confirm'
}

function normalizeOptions(options: DialogOptions | string): DialogOptions {
  return typeof options === 'string' ? { message: options } : options
}

export function DialogProvider({ children }: { children: ReactNode }) {
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const resolveRef = useRef<((value: boolean) => void) | null>(null)

  const close = useCallback((result: boolean) => {
    setDialog(null)
    resolveRef.current?.(result)
    resolveRef.current = null
  }, [])

  const showAlert = useCallback((options: DialogOptions | string) => {
    return new Promise<void>((resolve) => {
      resolveRef.current = () => resolve()
      setDialog({ kind: 'alert', ...normalizeOptions(options) })
    })
  }, [])

  const showConfirm = useCallback((options: DialogOptions | string) => {
    return new Promise<boolean>((resolve) => {
      resolveRef.current = resolve
      setDialog({ kind: 'confirm', ...normalizeOptions(options) })
    })
  }, [])

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

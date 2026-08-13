import { createContext, useContext } from 'react'

export interface DialogOptions {
  title?: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
}

export interface DialogContextValue {
  showAlert: (options: DialogOptions | string) => Promise<void>
  showConfirm: (options: DialogOptions | string) => Promise<boolean>
}

export const DialogContext = createContext<DialogContextValue | null>(null)

export function useDialog(): DialogContextValue {
  const context = useContext(DialogContext)
  if (!context) {
    throw new Error('useDialog must be used within a DialogProvider')
  }
  return context
}

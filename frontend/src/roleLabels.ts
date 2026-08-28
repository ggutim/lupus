import type { Role } from './api/rooms'

export const ROLE_LABELS: Record<Role, string> = {
  WEREWOLF: 'Lupo mannaro',
  PRIEST: 'Sacerdote',
  GRAVEDIGGER: 'Becchino',
  IDIOT: 'Idiota',
  CORRUPTED_JUDGE: 'Giudice corrotto',
  SURVIVOR: 'Sopravvissuto',
  VILLAGER: 'Contadino',
  GHOST: 'Fantasma',
  ANGEL: 'Angelo',
  GUARDIAN: 'Guardiano',
  KILLER: 'Killer',
}

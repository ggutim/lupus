import type { Role } from './api/rooms'

export type RoleAlignment = 'GOOD' | 'EVIL'

export const ROLE_ALIGNMENT: Record<Role, RoleAlignment> = {
  WEREWOLF: 'EVIL',
  PRIEST: 'GOOD',
  GRAVEDIGGER: 'GOOD',
  IDIOT: 'GOOD',
  CORRUPTED_JUDGE: 'EVIL',
  SURVIVOR: 'GOOD',
  VILLAGER: 'GOOD',
}

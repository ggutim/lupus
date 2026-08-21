import type { ReactNode } from 'react'
import type { Role } from './api/rooms'
import {
  CorruptedJudgeIcon,
  GravediggerIcon,
  IdiotIcon,
  PriestIcon,
  SurvivorIcon,
  VillagerIcon,
  WerewolfIcon,
} from './components/icons'

/** Every role a master can pick a count for at room creation — everything but the afterlife-only ghost/angel. */
export type AssignableRole = Exclude<Role, 'GHOST' | 'ANGEL'>

/**
 * Master-facing rule text for the room-creation role picker's info
 * popups — third person, since the master is configuring the ruleset,
 * not playing the role. Deliberately separate from `PlayerRolePage`'s
 * `ROLE_INFO`, which addresses the role's own holder directly ("scegli
 * chi sbranare") — that phrasing would misaddress the master here.
 */
export const ROLE_SETUP_INFO: Record<AssignableRole, { icon: ReactNode; description: string }> = {
  WEREWOLF: {
    icon: <WerewolfIcon />,
    description:
      'Ogni notte, i lupi mannari si svegliano insieme e scelgono in silenzio un giocatore da eliminare. Non possono scegliere un altro lupo. Vincono quando arrivano a essere tanti quanto i buoni rimasti in vita.',
  },
  PRIEST: {
    icon: <PriestIcon />,
    description:
      'Ogni notte, il sacerdote sceglie un giocatore vivo e ne scopre l\'allineamento: buono o malvagio. Il risultato resta segreto — tocca al sacerdote decidere se e come usarlo di giorno.',
  },
  GRAVEDIGGER: {
    icon: <GravediggerIcon />,
    description:
      'Ogni notte, se c\'è almeno un morto, il becchino può scegliere un giocatore morto e scoprirne l\'allineamento: buono o malvagio.',
  },
  IDIOT: {
    icon: <IdiotIcon />,
    description:
      'Non ha poteri e gioca per conto proprio: non fa parte né dei buoni né dei malvagi. Se il villaggio lo elimina con il voto, vince subito da solo — quindi la sua sola presenza rende ogni votazione più rischiosa.',
  },
  CORRUPTED_JUDGE: {
    icon: <CorruptedJudgeIcon />,
    description:
      'Se durante il giorno nessuno viene eliminato dal voto, la notte seguente il giudice corrotto può eliminare chi vuole, anche un lupo mannaro. Può scegliere di non usare il potere quella notte.',
  },
  SURVIVOR: {
    icon: <SurvivorIcon />,
    description:
      'Un contadino come gli altri, ma con una vita in più: la prima volta che i lupi mannari lo sbranano, sopravvive. Il giudice corrotto lo elimina comunque, senza che quella vita extra lo protegga.',
  },
  VILLAGER: {
    icon: <VillagerIcon />,
    description:
      'Nessun potere speciale: osserva, discute e vota. È il ruolo di riempimento — ogni giocatore senza un ruolo speciale assegnato diventa un contadino.',
  },
}

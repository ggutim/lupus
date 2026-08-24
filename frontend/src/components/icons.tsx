import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

export function WerewolfIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M14 46c-3-10-2-20 4-27-1-4 1-8 4-9 1 3 2 5 4 6 3-2 7-2 10 0 2-1 3-3 4-6 3 1 5 5 4 9 6 7 7 17 4 27-2-3-5-5-8-5-2 4-5 7-8 7s-6-3-8-7c-3 0-6 2-10 5Z"
        fill="currentColor"
      />
      <path d="M22 30l4 6 6-4 6 4 4-6" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="26" cy="34" r="1.6" fill="#fff" />
      <circle cx="38" cy="34" r="1.6" fill="#fff" />
    </svg>
  )
}

export function PriestIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M32 8c9 0 15 8 15 17v5H17v-5c0-9 6-17 15-17Z" fill="currentColor" />
      <rect x="15" y="30" width="34" height="6" rx="2" fill="currentColor" />
      <rect x="22" y="38" width="20" height="18" rx="3" fill="currentColor" />
      <path d="M32 14v8M28 18h8" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  )
}

export function GravediggerIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <rect x="20" y="4" width="24" height="6" rx="3" fill="currentColor" />
      <rect x="29" y="8" width="6" height="20" rx="3" fill="currentColor" />
      <path d="M24 28h16v18a8 8 0 0 1-16 0V28Z" fill="currentColor" />
      <path d="M24 36h16" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function IdiotIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M14 40 20 14l8 12 4-18 4 18 8-12 6 26c-6-4-13-6-20-6s-14 2-20 6Z" fill="currentColor" />
      <circle cx="20" cy="12" r="3" fill="currentColor" />
      <circle cx="32" cy="8" r="3" fill="currentColor" />
      <circle cx="44" cy="12" r="3" fill="currentColor" />
      <path d="M20 44q12 8 24 0" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" fill="none" />
    </svg>
  )
}

export function CorruptedJudgeIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <rect x="8" y="38" width="22" height="8" rx="3" fill="currentColor" />
      <rect x="26" y="10" width="14" height="24" rx="3" fill="currentColor" transform="rotate(-40 33 22)" />
      <rect x="40" y="4" width="18" height="8" rx="3" fill="currentColor" transform="rotate(-40 49 8)" />
      <path d="M14 50h16" stroke="#fff" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

export function SurvivorIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M32 6 14 13v15c0 15 8 25 18 30 10-5 18-15 18-30V13Z" fill="currentColor" />
      <path d="M24 30l6 6 10-12" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </svg>
  )
}

export function VillagerIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <circle cx="32" cy="18" r="9" fill="currentColor" />
      <path d="M14 52c0-11 8-18 18-18s18 7 18 18v2H14v-2Z" fill="currentColor" />
    </svg>
  )
}

export function GhostIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M16 26A16 16 0 0 1 48 26V44L43 50L38 44L32 50L26 44L21 50L16 44Z"
        fill="currentColor"
      />
      <circle cx="26" cy="30" r="2.6" fill="#fff" />
      <circle cx="38" cy="30" r="2.6" fill="#fff" />
    </svg>
  )
}

export function AngelIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <ellipse cx="32" cy="10" rx="8" ry="4" fill="none" stroke="currentColor" strokeWidth="3" />
      <circle cx="32" cy="24" r="8" fill="currentColor" />
      <path d="M14 30c8 2 12 8 12 8s-10 2-16-4c-2-2 0-5 4-4Z" fill="currentColor" />
      <path d="M50 30c-8 2-12 8-12 8s10 2 16-4c2-2 0-5-4-4Z" fill="currentColor" />
      <path d="M16 54c0-9 7-15 16-15s16 6 16 15v2H16v-2Z" fill="currentColor" />
    </svg>
  )
}

export function GuardianIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M32 6 50 13v14c0 14-8 23-18 29-10-6-18-15-18-29V13Z" fill="currentColor" />
      <path
        d="M32 20 41 24v9c0 8-5 13-9 16-4-3-9-8-9-16v-9Z"
        stroke="#fff"
        strokeWidth="2.4"
        strokeLinejoin="round"
        fill="none"
      />
    </svg>
  )
}

export function KillerIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <circle cx="32" cy="8" r="4" fill="currentColor" />
      <rect x="29" y="10" width="6" height="14" rx="3" fill="currentColor" />
      <rect x="18" y="24" width="28" height="6" rx="2" fill="currentColor" />
      <path d="M32 30 40 30 32 56 24 30Z" fill="currentColor" />
      <path d="M32 33v17" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function MeepleIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M32 6c5 0 8 4 8 8 0 3-1 5-3 7l9 11c2 3 1 6-1 8l-6-5v14c0 4-3 7-7 7s-7-3-7-7V35l-6 5c-2-2-3-5-1-8l9-11c-2-2-3-4-3-7 0-4 3-8 8-8Z"
        fill="currentColor"
      />
    </svg>
  )
}

export function SkullIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M32 8c-11 0-19 8-19 18 0 6 3 11 7 14v8c0 2 2 4 4 4h4v-6h4v6h4v-6h4v6h4c2 0 4-2 4-4v-8c4-3 7-8 7-14 0-10-8-18-19-18Z"
        fill="currentColor"
      />
      <circle cx="24" cy="28" r="4" fill="#1a1108" />
      <circle cx="40" cy="28" r="4" fill="#1a1108" />
      <path d="M29 36h6l-3 5-3-5Z" fill="#1a1108" />
    </svg>
  )
}

export function MoonIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M10 32A22 22 0 1 1 54 32A22 22 0 1 1 10 32ZM17 30A18 18 0 1 1 53 30A18 18 0 1 1 17 30Z"
        fill="currentColor"
      />
    </svg>
  )
}

export function SunIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <circle cx="32" cy="32" r="12" fill="currentColor" />
      <g stroke="currentColor" strokeWidth="4" strokeLinecap="round">
        <path d="M32 6v8M32 50v8M6 32h8M50 32h8" />
        <path d="M13 13l6 6M45 45l6 6M51 13l-6 6M19 45l-6 6" />
      </g>
    </svg>
  )
}

export function EyeIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M32 16c-14 0-24 11-27 16 3 5 13 16 27 16s24-11 27-16c-3-5-13-16-27-16Z"
        fill="currentColor"
      />
      <circle cx="32" cy="32" r="8" fill="#1a1108" />
      <circle cx="32" cy="32" r="3" fill="currentColor" />
    </svg>
  )
}

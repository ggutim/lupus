import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

export function WerewolfIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M14 46c-3-10-2-20 4-27-1-4 1-8 4-9 1 3 2 5 4 6 3-2 7-2 10 0 2-1 3-3 4-6 3 1 5 5 4 9 6 7 7 17 4 27-2-3-5-5-8-5-2 4-5 7-8 7s-6-3-8-7c-3 0-6 2-10 5Z"
        fill="currentColor"
      />
      <path d="M22 30l4 6 6-4 6 4 4-6" stroke="#1a1108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="26" cy="34" r="1.6" fill="#1a1108" />
      <circle cx="38" cy="34" r="1.6" fill="#1a1108" />
    </svg>
  )
}

export function PriestIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path d="M32 8c9 0 16 7 16 16v6H16v-6c0-9 7-16 16-16Z" fill="currentColor" />
      <rect x="14" y="30" width="36" height="6" rx="2" fill="currentColor" />
      <path d="M24 40h16v14a8 8 0 0 1-16 0V40Z" fill="currentColor" />
      <path d="M32 14v10M27 19h10" stroke="#1a1108" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  )
}

export function GravediggerIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <path
        d="M22 58V34a10 10 0 0 1 20 0v24Z"
        fill="currentColor"
      />
      <path d="M22 44h20" stroke="#1a1108" strokeWidth="2.4" strokeLinecap="round" />
      <path d="M32 34v10" stroke="#1a1108" strokeWidth="2.4" strokeLinecap="round" />
      <path
        d="M14 20l10 8"
        stroke="currentColor"
        strokeWidth="5"
        strokeLinecap="round"
      />
      <rect x="8" y="10" width="14" height="6" rx="2" transform="rotate(-32 8 10)" fill="currentColor" />
    </svg>
  )
}

export function VillagerIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
      <circle cx="32" cy="18" r="9" fill="currentColor" />
      <path d="M14 52c0-11 8-18 18-18s18 7 18 18v2H14v-2Z" fill="currentColor" />
      <path d="M20 30l-6 20M44 30l6 20" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
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
        d="M42 8c-13 2-22 13-22 26s9 24 22 26C50 56 56 45 56 32c0-3-.4-6-1-9-4 6-11 10-19 10-12 0-22-10-22-22 0-3 .6-6 1.6-9C21 1 32-1 42 8Z"
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

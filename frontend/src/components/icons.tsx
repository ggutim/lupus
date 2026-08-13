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

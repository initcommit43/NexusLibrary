/** The mark for a favourite, hollow until it is one. Drawn in one place, used wherever. */
export const Heart = ({ filled, size = 16 }: { filled: boolean; size?: number }) => (
  <svg
    viewBox="0 0 24 24"
    width={size}
    height={size}
    fill={filled ? 'currentColor' : 'none'}
    stroke="currentColor"
    aria-hidden
  >
    <path
      d="M12 20s-7-4.35-7-9a4 4 0 0 1 7-2.65A4 4 0 0 1 19 11c0 4.65-7 9-7 9Z"
      strokeWidth="1.8"
      strokeLinejoin="round"
    />
  </svg>
)

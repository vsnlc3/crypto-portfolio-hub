export function formatAmount(value: number, unit?: string) {
  const abs = Math.abs(value)
  const maximumFractionDigits = abs >= 1000 ? 2 : abs >= 1 ? 4 : 6
  const formatted = new Intl.NumberFormat('en-US', { maximumFractionDigits }).format(value)
  return unit ? `${formatted} ${unit}` : formatted
}

export function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

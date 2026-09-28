import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import type { PortfolioDataStatus } from '@/lib/portfolio-api'
import { cn } from '@/lib/utils'

const statusClasses: Record<PortfolioDataStatus, string> = {
  COMPLETE: 'bg-positive/12 text-positive',
  STALE: 'bg-amber-500/12 text-amber-400',
  PARTIAL: 'bg-amber-500/12 text-amber-400',
  UNAVAILABLE: 'bg-muted text-muted-foreground',
}

export function DataStatusBadge({ status }: { status: PortfolioDataStatus }) {
  const label = status === 'COMPLETE' ? 'Fresh' : status[0] + status.slice(1).toLowerCase()
  return <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-medium', statusClasses[status])}>{label}</span>
}

export function LoadingCard({ label }: { label: string }) {
  return (
    <Card className="gap-5 p-6" aria-label={`Loading ${label}`} aria-busy="true">
      <div className="h-5 w-40 animate-pulse rounded bg-muted" />
      <div className="h-10 w-52 animate-pulse rounded bg-muted/70" />
      <div className="h-32 animate-pulse rounded bg-muted/30" />
    </Card>
  )
}

export function InlineError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2.5 text-xs text-destructive">
      <AlertTriangle className="size-4 shrink-0" />
      <p className="flex-1">{message}</p>
      <Button variant="outline" size="sm" onClick={onRetry}>Try again</Button>
    </div>
  )
}

export function EmptyCard({ title, detail, action }: {
  title: string
  detail: string
  action?: React.ReactNode
}) {
  return (
    <Card className="items-center gap-2 p-8 text-center">
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-md text-xs text-muted-foreground">{detail}</p>
      {action}
    </Card>
  )
}

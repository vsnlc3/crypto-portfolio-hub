'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Plus, RefreshCw, Trash2, TriangleAlert, WalletCards } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm, type FieldPath } from 'react-hook-form'
import { z } from 'zod'
import { PageHeader } from '@/components/page-header'
import { ServiceBadge } from '@/components/service-badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import {
  ConnectionsApiError,
  connectionsQueryKey,
  createConnection,
  deleteConnection,
  getConnections,
  type Connection,
  type ConnectionCreateRequest,
  type ConnectionProvider,
  type ConnectionStatus,
  type SyncCapability,
} from '@/lib/connections-api'
import { fmtRelative } from '@/lib/mock-data'

const base58Alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'

function isSolanaAddress(value: string) {
  const address = value.trim()
  if (address.length < 32 || address.length > 44) return false

  const decoded = new Array<number>(32).fill(0)
  for (const character of address) {
    const digit = base58Alphabet.indexOf(character)
    if (digit < 0) return false
    let carry = digit
    for (let index = decoded.length - 1; index >= 0; index--) {
      carry += decoded[index] * 58
      decoded[index] = carry & 0xff
      carry >>= 8
    }
    if (carry > 0) return false
  }

  let leadingZeroBytes = 0
  while (leadingZeroBytes < address.length && address[leadingZeroBytes] === '1') leadingZeroBytes++
  let firstNonZeroByte = 0
  while (firstNonZeroByte < decoded.length && decoded[firstNonZeroByte] === 0) firstNonZeroByte++
  const significantBytes = decoded.length - firstNonZeroByte
  return leadingZeroBytes + significantBytes === 32
}

const connectionFormSchema = z.object({
  provider: z.enum(['BITBANK', 'SOLANA', 'HYPERLIQUID']),
  displayName: z.string().max(100, 'Use 100 characters or fewer.'),
  apiKey: z.string().max(255, 'Use 255 characters or fewer.'),
  apiSecret: z.string().max(1024, 'Use 1024 characters or fewer.'),
  walletAddress: z.string().max(44, 'Use a valid Solana address.'),
  accountAddress: z.string().max(42, 'Use a valid Hyperliquid address.'),
}).superRefine((values, context) => {
  if (values.provider === 'BITBANK') {
    if (!values.apiKey.trim()) context.addIssue({ code: 'custom', path: ['apiKey'], message: 'API Key is required.' })
    if (!values.apiSecret.trim()) context.addIssue({ code: 'custom', path: ['apiSecret'], message: 'API Secret is required.' })
    return
  }

  if (values.provider === 'SOLANA' && !isSolanaAddress(values.walletAddress)) {
    context.addIssue({ code: 'custom', path: ['walletAddress'], message: 'Enter a valid 32-byte Solana address.' })
  }

  if (values.provider === 'HYPERLIQUID' && !/^0x[0-9a-fA-F]{40}$/.test(values.accountAddress.trim())) {
    context.addIssue({ code: 'custom', path: ['accountAddress'], message: 'Enter a 0x address with 40 hexadecimal characters.' })
  }
})

type ConnectionFormValues = z.infer<typeof connectionFormSchema>
type ToastMessage = { id: number; message: string } | null

const initialFormValues: ConnectionFormValues = {
  provider: 'BITBANK',
  displayName: '',
  apiKey: '',
  apiSecret: '',
  walletAddress: '',
  accountAddress: '',
}

const statusMeta: Record<
  ConnectionStatus,
  { label: string; className: string; icon: typeof Check }
> = {
  CONNECTED: { label: 'Connected', className: 'bg-positive/12 text-positive', icon: Check },
  SYNCING: { label: 'Syncing', className: 'bg-primary/12 text-primary', icon: RefreshCw },
  ERROR: { label: 'Error', className: 'bg-negative/12 text-negative', icon: TriangleAlert },
  DISCONNECTED: { label: 'Disconnected', className: 'bg-muted text-muted-foreground', icon: TriangleAlert },
}

const providerMeta: Record<ConnectionProvider, { name: string; kind: string; badge: 'bitbank' | 'phantom' | 'hyperliquid' }> = {
  BITBANK: { name: 'bitbank', kind: 'Exchange', badge: 'bitbank' },
  SOLANA: { name: 'Phantom', kind: 'Solana Wallet', badge: 'phantom' },
  HYPERLIQUID: { name: 'Hyperliquid', kind: 'DeFi protocol', badge: 'hyperliquid' },
}

const capabilityLabels: Record<SyncCapability, string> = {
  BALANCE: 'spot',
  POSITION: 'perp',
  ACTIVITY: 'history',
  ACCOUNT: 'account',
}

export default function ConnectionsPage() {
  const queryClient = useQueryClient()
  const connectionsQuery = useQuery({
    queryKey: connectionsQueryKey,
    queryFn: getConnections,
  })
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [toast, setToast] = useState<ToastMessage>(null)
  const [deleteError, setDeleteError] = useState(false)

  const deleteMutation = useMutation({
    mutationFn: deleteConnection,
    onSuccess: async () => {
      setDeleteError(false)
      await queryClient.invalidateQueries({ queryKey: connectionsQueryKey })
      setToast({ id: Date.now(), message: 'Connection removed.' })
    },
    onError: () => setDeleteError(true),
  })

  useEffect(() => {
    if (!toast) return
    const timeout = window.setTimeout(() => setToast(null), 4000)
    return () => window.clearTimeout(timeout)
  }, [toast])

  function handleDelete(connection: Connection) {
    if (deleteMutation.isPending) return
    const name = connection.displayName || providerMeta[connection.provider].name
    if (!window.confirm(`Remove ${name} from your Connections? Its Activity and Sync history will be retained.`)) return
    setDeleteError(false)
    deleteMutation.mutate(connection.id)
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Connections"
        subtitle="Manage the exchanges and wallets feeding your portfolio. Connections are read-only."
        actions={
          <Button
            size="sm"
            className="gap-2"
            onClick={() => setShowCreateForm((visible) => !visible)}
            aria-expanded={showCreateForm}
            aria-controls="connection-create-form"
          >
            <Plus className="size-4" />
            Add source
          </Button>
        }
      />

      {toast && (
        <div
          key={toast.id}
          role="status"
          aria-live="polite"
          className="mb-4 rounded-lg border border-positive/30 bg-positive/10 px-4 py-3 text-sm text-positive"
        >
          {toast.message}
        </div>
      )}

      {deleteError && (
        <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          Connection could not be removed. Refresh the list and try again.
        </p>
      )}

      {showCreateForm && (
        <ConnectionCreateForm
          onCancel={() => setShowCreateForm(false)}
          onCreated={() => {
            setShowCreateForm(false)
            setToast({ id: Date.now(), message: 'Connection added.' })
          }}
        />
      )}

      {connectionsQuery.isPending ? (
        <div className="space-y-3" aria-label="Loading connections" aria-busy="true">
          {[0, 1].map((item) => (
            <Card key={item} className="gap-0 p-5">
              <div className="flex items-center gap-4">
                <div className="size-11 animate-pulse rounded-xl bg-muted" />
                <div className="flex-1 space-y-2">
                  <div className="h-4 w-36 animate-pulse rounded bg-muted" />
                  <div className="h-3 w-48 animate-pulse rounded bg-muted" />
                </div>
                <div className="h-6 w-20 animate-pulse rounded-full bg-muted" />
              </div>
              <div className="mt-4 h-12 animate-pulse rounded bg-muted/60" />
            </Card>
          ))}
        </div>
      ) : connectionsQuery.isError && connectionsQuery.data === undefined ? (
        <Card role="alert" className="items-center px-6 py-10 text-center">
          <TriangleAlert className="size-6 text-destructive" aria-hidden="true" />
          <h2 className="mt-3 font-semibold">Connections couldn’t be loaded</h2>
          <p className="mt-1 text-sm text-muted-foreground">The list is unavailable. Try again to load your saved Connections.</p>
          <Button className="mt-4" variant="outline" onClick={() => void connectionsQuery.refetch()}>
            Try again
          </Button>
        </Card>
      ) : (
        <>
          {connectionsQuery.isError && (
            <div role="alert" className="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-muted/40 px-4 py-3 text-sm">
              <p className="text-muted-foreground">Could not refresh the list. Showing the last retrieved Connections.</p>
              <Button size="sm" variant="outline" onClick={() => void connectionsQuery.refetch()}>
                Try again
              </Button>
            </div>
          )}
          {connectionsQuery.data?.length === 0 ? (
            <Card className="items-center px-6 py-12 text-center">
              <div className="flex size-12 items-center justify-center rounded-xl bg-accent text-primary">
                <WalletCards className="size-6" aria-hidden="true" />
              </div>
              <h2 className="mt-4 font-semibold">No connections yet</h2>
              <p className="mt-1 max-w-sm text-sm text-muted-foreground">
                Add a read-only exchange, Solana address, or Hyperliquid account to start building your portfolio.
              </p>
              <Button className="mt-5 gap-2" onClick={() => setShowCreateForm(true)}>
                <Plus className="size-4" />
                Add your first source
              </Button>
            </Card>
          ) : (
            <div className="space-y-3">
              {connectionsQuery.data?.map((connection) => (
                <ConnectionCard
                  key={connection.id}
                  connection={connection}
                  deleting={deleteMutation.isPending}
                  onDelete={() => handleDelete(connection)}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}

function ConnectionCard({
  connection,
  deleting,
  onDelete,
}: {
  connection: Connection
  deleting: boolean
  onDelete: () => void
}) {
  const provider = providerMeta[connection.provider]
  const status = statusMeta[connection.status]
  const StatusIcon = status.icon

  return (
    <Card className="gap-0 p-5">
      <div className="flex flex-wrap items-center gap-4">
        <ServiceBadge id={provider.badge} size={44} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold">{connection.displayName || provider.name}</p>
            <span className="text-[11px] uppercase tracking-wide text-muted-foreground">{provider.kind}</span>
          </div>
          <p className="mt-0.5 break-all font-mono text-xs text-muted-foreground">
            {connection.maskedIdentifier ?? (connection.provider === 'BITBANK' ? 'Read-only API credentials' : 'Account identifier unavailable')}
          </p>
        </div>
        <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium', status.className)}>
          <StatusIcon className={cn('size-3.5', connection.status === 'SYNCING' && 'animate-spin')} />
          {status.label}
        </span>
      </div>

      <div className="mt-4 flex flex-wrap items-end justify-between gap-4 border-t border-border pt-4">
        <div className="flex flex-wrap gap-8">
          <div>
            <p className="text-[11px] text-muted-foreground">Value tracked</p>
            <p className="mt-0.5 font-mono text-lg font-semibold tabular">—</p>
            <p className="text-[11px] text-muted-foreground">Not valued yet</p>
          </div>
          <div>
            <p className="text-[11px] text-muted-foreground">Capabilities</p>
            <div className="mt-1 flex flex-wrap gap-1.5">
              {connection.capabilities.map((capability) => (
                <span
                  key={capability}
                  className="rounded-md border border-border bg-accent/50 px-2 py-0.5 text-[11px] font-medium uppercase"
                >
                  {capabilityLabels[capability]}
                </span>
              ))}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="space-y-0.5 text-[11px] text-muted-foreground">
            {connection.lastSuccessAt && <p>Last successful sync {fmtRelative(connection.lastSuccessAt)}</p>}
            {connection.lastAttemptAt && <p>Last attempt {fmtRelative(connection.lastAttemptAt)}</p>}
            {!connection.lastSuccessAt && !connection.lastAttemptAt && <p>Not synced yet</p>}
          </div>
          <Button variant="destructive" size="sm" className="gap-2" onClick={onDelete} disabled={deleting}>
            <Trash2 className="size-3.5" />
            Disconnect
          </Button>
        </div>
      </div>
    </Card>
  )
}

function ConnectionCreateForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: () => void }) {
  const queryClient = useQueryClient()
  const [requestError, setRequestError] = useState<string | null>(null)
  const form = useForm<ConnectionFormValues>({
    resolver: zodResolver(connectionFormSchema),
    defaultValues: initialFormValues,
  })
  const provider = form.watch('provider')
  const createMutation = useMutation({
    mutationFn: createConnection,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: connectionsQueryKey })
      onCreated()
    },
    onError: (error) => {
      if (error instanceof ConnectionsApiError && Object.keys(error.fieldErrors).length > 0) {
        const validFields = new Set<FieldPath<ConnectionFormValues>>([
          'displayName', 'apiKey', 'apiSecret', 'walletAddress', 'accountAddress',
        ])
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (validFields.has(field as FieldPath<ConnectionFormValues>)) {
            form.setError(field as FieldPath<ConnectionFormValues>, { type: 'server', message })
          }
        }
        setRequestError('Review the highlighted fields and try again.')
        return
      }

      setRequestError(error instanceof ConnectionsApiError && error.code === 'CONNECTION_ALREADY_EXISTS'
        ? 'This account is already connected.'
        : error instanceof ConnectionsApiError && error.code === 'CREDENTIAL_ENCRYPTION_UNAVAILABLE'
          ? 'Credential encryption is not configured. Contact the application operator.'
          : 'Connection could not be saved. Please try again.')
    },
  })

  function changeProvider(nextProvider: ConnectionProvider) {
    form.reset({ ...initialFormValues, provider: nextProvider, displayName: form.getValues('displayName') })
    setRequestError(null)
  }

  function toRequest(values: ConnectionFormValues): ConnectionCreateRequest {
    const displayName = values.displayName.trim() || undefined
    if (values.provider === 'BITBANK') {
      return { provider: values.provider, displayName, apiKey: values.apiKey.trim(), apiSecret: values.apiSecret.trim() }
    }
    if (values.provider === 'SOLANA') {
      return { provider: values.provider, displayName, walletAddress: values.walletAddress.trim() }
    }
    return { provider: values.provider, displayName, accountAddress: values.accountAddress.trim() }
  }

  function submit(values: ConnectionFormValues) {
    setRequestError(null)
    createMutation.mutate(toRequest(values))
  }

  return (
    <Card id="connection-create-form" className="mb-4 gap-0 p-5" aria-labelledby="connection-form-title">
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <h2 id="connection-form-title" className="font-semibold">Add a read-only source</h2>
          <p className="mt-1 text-xs text-muted-foreground">No trades, withdrawals, or signing permissions are requested.</p>
        </div>
        <Button type="button" size="sm" variant="ghost" onClick={onCancel} disabled={createMutation.isPending}>
          Cancel
        </Button>
      </div>

      <form onSubmit={form.handleSubmit(submit)} noValidate className="space-y-4">
        <Field label="Service" name="provider">
          <select
            id="provider"
            value={provider}
            onChange={(event) => changeProvider(event.target.value as ConnectionProvider)}
            className={inputClassName}
          >
            <option value="BITBANK">bitbank</option>
            <option value="SOLANA">Solana Wallet Address (shown as Phantom)</option>
            <option value="HYPERLIQUID">Hyperliquid</option>
          </select>
        </Field>

        <Field label="Display name (optional)" name="displayName" error={form.formState.errors.displayName?.message}>
          <input id="displayName" autoComplete="off" {...form.register('displayName')} className={inputClassName} maxLength={100} />
        </Field>

        {provider === 'BITBANK' && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Read-only API Key" name="apiKey" error={form.formState.errors.apiKey?.message}>
              <input
                id="apiKey"
                type="password"
                autoComplete="new-password"
                spellCheck={false}
                {...form.register('apiKey')}
                className={inputClassName}
                maxLength={255}
              />
            </Field>
            <Field label="API Secret" name="apiSecret" error={form.formState.errors.apiSecret?.message}>
              <input
                id="apiSecret"
                type="password"
                autoComplete="new-password"
                spellCheck={false}
                {...form.register('apiSecret')}
                className={inputClassName}
                maxLength={1024}
              />
            </Field>
            <p className="text-xs leading-relaxed text-muted-foreground sm:col-span-2">
              Create an API key with read-only permissions in bitbank. Values are encrypted by the Backend and are not shown again.
            </p>
          </div>
        )}

        {provider === 'SOLANA' && (
          <Field label="Solana Wallet Address" name="walletAddress" error={form.formState.errors.walletAddress?.message}>
            <input
              id="walletAddress"
              autoComplete="off"
              spellCheck={false}
              {...form.register('walletAddress')}
              className={inputClassName}
              maxLength={44}
              placeholder="Base58 address"
            />
            <p className="mt-1.5 text-xs text-muted-foreground">Enter the public address. A seed phrase, private key, or wallet signature is never requested.</p>
          </Field>
        )}

        {provider === 'HYPERLIQUID' && (
          <Field label="Hyperliquid account address" name="accountAddress" error={form.formState.errors.accountAddress?.message}>
            <input
              id="accountAddress"
              autoComplete="off"
              spellCheck={false}
              {...form.register('accountAddress')}
              className={inputClassName}
              maxLength={42}
              placeholder="0x followed by 40 hexadecimal characters"
            />
            <p className="mt-1.5 text-xs text-muted-foreground">Enter the account to read. No private key or agent secret is needed.</p>
          </Field>
        )}

        {requestError && <p role="alert" className="text-sm text-destructive">{requestError}</p>}

        <div className="flex justify-end gap-2 border-t border-border pt-4">
          <Button type="button" variant="outline" onClick={onCancel} disabled={createMutation.isPending}>Cancel</Button>
          <Button type="submit" disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Saving…' : 'Add connection'}
          </Button>
        </div>
      </form>
    </Card>
  )
}

function Field({
  label,
  name,
  error,
  children,
}: {
  label: string
  name: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="min-w-0 space-y-1.5">
      <label htmlFor={name} className="block text-xs font-medium">{label}</label>
      {children}
      {error && <p role="alert" className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

const inputClassName =
  'h-9 w-full rounded-lg border border-border bg-background px-3 text-sm outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring/40 aria-invalid:border-destructive'

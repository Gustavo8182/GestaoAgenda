export interface AuditEntry {
  readonly id: string;
  readonly actorName: string;
  readonly action: string;
  readonly entityType: string | null;
  readonly entityId: string | null;
  readonly metadata: Record<string, string>;
  readonly occurredAt: string;
}

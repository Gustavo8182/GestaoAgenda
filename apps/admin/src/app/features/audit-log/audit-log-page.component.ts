import { DatePipe, KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AuditEntry } from '../../core/auditing/audit-entry';
import { AuditTrailService } from '../../core/auditing/audit-trail.service';

const ACTION_LABELS: Record<string, string> = {
  APPOINTMENT_CREATED: 'Agendamento criado',
  APPOINTMENT_RESCHEDULED: 'Agendamento remarcado',
  APPOINTMENT_CANCELLED: 'Agendamento cancelado',
  APPOINTMENT_CONFIRMED: 'Agendamento confirmado',
  APPOINTMENT_ARRIVED: 'Chegada registrada',
  APPOINTMENT_STARTED: 'Atendimento iniciado',
  APPOINTMENT_COMPLETED: 'Atendimento concluído',
  APPOINTMENT_NO_SHOW: 'Falta registrada',
  CLIENT_CREATED: 'Cliente cadastrada',
  SERVICE_CREATED: 'Serviço cadastrado',
  SERVICE_DEACTIVATED: 'Serviço inativado',
  BUSINESS_HOURS_UPDATED: 'Horário de funcionamento atualizado',
  BLOCK_CREATED: 'Bloqueio criado',
  BLOCK_REMOVED: 'Bloqueio removido'
};

const ENTITY_TYPE_LABELS: Record<string, string> = {
  APPOINTMENT: 'Agendamento',
  CLIENT: 'Cliente',
  SERVICE: 'Serviço',
  BLOCK: 'Bloqueio',
  BUSINESS_HOURS: 'Horário de funcionamento'
};

@Component({
  selector: 'app-audit-log-page',
  imports: [DatePipe, KeyValuePipe],
  templateUrl: './audit-log-page.component.html',
  styleUrl: './audit-log-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuditLogPageComponent {
  private readonly auditTrailService = inject(AuditTrailService);

  protected readonly entries = signal<AuditEntry[]>([]);
  protected readonly loading = signal(true);

  constructor() {
    this.auditTrailService.recent().subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected actionLabel(action: string): string {
    return ACTION_LABELS[action] ?? action;
  }

  protected entityTypeLabel(entityType: string | null): string | null {
    if (!entityType) {
      return null;
    }
    return ENTITY_TYPE_LABELS[entityType] ?? entityType;
  }

  protected hasMetadata(entry: AuditEntry): boolean {
    return Object.keys(entry.metadata ?? {}).length > 0;
  }
}

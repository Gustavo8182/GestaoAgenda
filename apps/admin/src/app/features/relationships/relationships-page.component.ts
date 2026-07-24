import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ServiceSummary } from '../../core/catalog/service-summary';
import { AssignableMember, RelationshipStatus, RelationshipSummary } from '../../core/relationships/relationship-summary';
import { RelationshipsService } from '../../core/relationships/relationships.service';

export const STATUS_LABELS: Record<RelationshipStatus, string> = {
  NEW_CONTACT: 'Novo contato',
  IN_SERVICE: 'Em atendimento',
  AWAITING_RESPONSE: 'Aguardando resposta',
  PENDING_APPOINTMENT: 'Agendamento pendente',
  SCHEDULED: 'Agendado',
  FOLLOW_UP_LATER: 'Acompanhar depois',
  DID_NOT_SCHEDULE: 'Não agendou',
  DO_NOT_CONTACT: 'Não contatar'
};

const STATUS_OPTIONS = Object.keys(STATUS_LABELS) as RelationshipStatus[];

function toLocalDateTimeInputValue(date: Date): string {
  const pad = (value: number) => value.toString().padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

@Component({
  selector: 'app-relationships-page',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './relationships-page.component.html',
  styleUrl: './relationships-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RelationshipsPageComponent {
  private readonly relationshipsService = inject(RelationshipsService);
  private readonly catalogService = inject(CatalogService);
  protected readonly authService = inject(AuthService);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly exportUrl = `${environment.apiBaseUrl}/v1/reports/export/relationships`;

  protected readonly services = signal<ServiceSummary[]>([]);
  protected readonly contacts = signal<RelationshipSummary[]>([]);
  protected readonly assignableMembers = signal<AssignableMember[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly editingId = signal<string | null>(null);
  protected readonly convertingId = signal<string | null>(null);
  protected readonly reassigningId = signal<string | null>(null);
  protected readonly rowActionError = signal<string | null>(null);
  protected readonly rowActionErrorId = signal<string | null>(null);
  protected readonly rowActionSubmitting = signal(false);

  protected readonly pendingCount = computed(
    () => this.contacts().filter((contact) => contact.pendingContact).length
  );

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    origin: new FormControl('', { nonNullable: true })
  });

  protected readonly editForm = new FormGroup({
    status: new FormControl<RelationshipStatus>('NEW_CONTACT', { nonNullable: true }),
    nextAction: new FormControl('', { nonNullable: true }),
    nextActionAt: new FormControl('', { nonNullable: true })
  });

  protected readonly convertForm = new FormGroup({
    serviceId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected readonly reassignForm = new FormGroup({
    responsibleUserId: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    this.reload();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const { name, phone, origin } = this.form.getRawValue();

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.relationshipsService.create(name, phone, origin).subscribe({
      next: (contact) => {
        this.contacts.update((current) => [...current, contact]);
        this.form.reset();
        this.submitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(error.error?.message ?? 'Não foi possível cadastrar. Confira os dados informados.');
      }
    });
  }

  protected startEdit(contact: RelationshipSummary): void {
    this.closeInlineForms();
    this.editingId.set(contact.id);
    this.editForm.setValue({
      status: contact.status,
      nextAction: contact.nextAction ?? '',
      nextActionAt: contact.nextActionAt ? toLocalDateTimeInputValue(new Date(contact.nextActionAt)) : ''
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.clearRowError();
  }

  protected confirmEdit(contact: RelationshipSummary): void {
    if (this.rowActionSubmitting()) {
      return;
    }

    const { status, nextAction, nextActionAt } = this.editForm.getRawValue();

    this.rowActionSubmitting.set(true);
    this.clearRowError();

    this.relationshipsService
      .update(contact.id, status, nextAction || null, nextActionAt ? new Date(nextActionAt).toISOString() : null)
      .subscribe({
        next: (updated) => {
          this.replaceContact(updated);
          this.editingId.set(null);
          this.rowActionSubmitting.set(false);
        },
        error: () => {
          this.rowActionSubmitting.set(false);
          this.setRowError(contact.id, 'Não foi possível atualizar este contato.');
        }
      });
  }

  protected startConvert(contact: RelationshipSummary): void {
    this.closeInlineForms();
    this.convertingId.set(contact.id);

    const defaultStart = new Date();
    defaultStart.setMinutes(0, 0, 0);
    defaultStart.setHours(defaultStart.getHours() + 1);
    this.convertForm.setValue({ serviceId: '', startAt: toLocalDateTimeInputValue(defaultStart) });
  }

  protected cancelConvertEdit(): void {
    this.convertingId.set(null);
    this.clearRowError();
  }

  protected confirmConvert(contact: RelationshipSummary): void {
    if (this.convertForm.invalid || this.rowActionSubmitting()) {
      this.convertForm.markAllAsTouched();
      return;
    }

    const { serviceId, startAt } = this.convertForm.getRawValue();
    const service = this.services().find((item) => item.id === serviceId);
    if (!service) {
      this.setRowError(contact.id, 'Selecione um serviço válido.');
      return;
    }

    const start = new Date(startAt);
    const end = new Date(start.getTime() + service.durationMinutes * 60_000);

    this.rowActionSubmitting.set(true);
    this.clearRowError();

    this.relationshipsService.convert(contact.id, serviceId, start.toISOString(), end.toISOString()).subscribe({
      next: () => {
        this.rowActionSubmitting.set(false);
        this.convertingId.set(null);
        this.reload();
      },
      error: (error: HttpErrorResponse) => {
        this.rowActionSubmitting.set(false);
        this.setRowError(
          contact.id,
          error.error?.message ?? 'Não foi possível converter. Confira o horário escolhido.'
        );
      }
    });
  }

  protected startReassign(contact: RelationshipSummary): void {
    this.closeInlineForms();
    this.reassigningId.set(contact.id);
    this.reassignForm.setValue({ responsibleUserId: contact.responsibleUserId });
  }

  protected cancelReassign(): void {
    this.reassigningId.set(null);
    this.clearRowError();
  }

  protected confirmReassign(contact: RelationshipSummary): void {
    if (this.reassignForm.invalid || this.rowActionSubmitting()) {
      this.reassignForm.markAllAsTouched();
      return;
    }

    const { responsibleUserId } = this.reassignForm.getRawValue();

    this.rowActionSubmitting.set(true);
    this.clearRowError();

    this.relationshipsService.reassign(contact.id, responsibleUserId).subscribe({
      next: (updated) => {
        this.replaceContact(updated);
        this.reassigningId.set(null);
        this.rowActionSubmitting.set(false);
      },
      error: () => {
        this.rowActionSubmitting.set(false);
        this.setRowError(contact.id, 'Não foi possível reatribuir o responsável.');
      }
    });
  }

  private closeInlineForms(): void {
    this.editingId.set(null);
    this.convertingId.set(null);
    this.reassigningId.set(null);
    this.clearRowError();
  }

  private replaceContact(updated: RelationshipSummary): void {
    this.contacts.update((current) => current.map((item) => (item.id === updated.id ? updated : item)));
  }

  private setRowError(contactId: string, message: string): void {
    this.rowActionErrorId.set(contactId);
    this.rowActionError.set(message);
  }

  private clearRowError(): void {
    this.rowActionErrorId.set(null);
    this.rowActionError.set(null);
  }

  private reload(): void {
    this.catalogService.list().subscribe((services) => this.services.set(services.filter((s) => s.active)));
    this.relationshipsService.listAssignableMembers().subscribe((members) => this.assignableMembers.set(members));

    this.loading.set(true);
    this.relationshipsService.list().subscribe({
      next: (contacts) => {
        this.contacts.set(contacts);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}

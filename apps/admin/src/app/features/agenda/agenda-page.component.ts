import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ServiceSummary } from '../../core/catalog/service-summary';
import { ClientSummary } from '../../core/clients/client-summary';
import { ClientsService } from '../../core/clients/clients.service';
import { AppointmentSummary } from '../../core/scheduling/appointment-summary';
import { SchedulingService } from '../../core/scheduling/scheduling.service';

function toLocalDateTimeInputValue(isoString: string): string {
  const date = new Date(isoString);
  const pad = (value: number) => value.toString().padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

@Component({
  selector: 'app-agenda-page',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './agenda-page.component.html',
  styleUrl: './agenda-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgendaPageComponent {
  private readonly clientsService = inject(ClientsService);
  private readonly catalogService = inject(CatalogService);
  private readonly schedulingService = inject(SchedulingService);

  protected readonly clients = signal<ClientSummary[]>([]);
  protected readonly services = signal<ServiceSummary[]>([]);
  protected readonly appointments = signal<AppointmentSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly reschedulingId = signal<string | null>(null);
  protected readonly cancellingId = signal<string | null>(null);
  protected readonly rowActionError = signal<string | null>(null);
  protected readonly rowActionSubmitting = signal(false);

  protected readonly form = new FormGroup({
    clientId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    serviceId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected readonly rescheduleForm = new FormGroup({
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected readonly cancelForm = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    this.reload();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const { clientId, serviceId, startAt } = this.form.getRawValue();
    const service = this.services().find((item) => item.id === serviceId);
    if (!service) {
      this.errorMessage.set('Selecione um serviço válido.');
      return;
    }

    const start = new Date(startAt);
    const end = new Date(start.getTime() + service.durationMinutes * 60_000);

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.schedulingService.create(clientId, serviceId, start.toISOString(), end.toISOString()).subscribe({
      next: (appointment) => {
        this.appointments.update((current) =>
          [...current, appointment].sort((a, b) => a.startAt.localeCompare(b.startAt))
        );
        this.form.reset();
        this.submitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        if (error.error?.code === 'appointment_conflict') {
          this.errorMessage.set('Já existe um agendamento nesse horário.');
        } else {
          this.errorMessage.set('Não foi possível criar o agendamento. Confira os dados informados.');
        }
      }
    });
  }

  protected startReschedule(appointment: AppointmentSummary): void {
    this.cancellingId.set(null);
    this.rowActionError.set(null);
    this.reschedulingId.set(appointment.id);
    this.rescheduleForm.setValue({ startAt: toLocalDateTimeInputValue(appointment.startAt) });
  }

  protected cancelRescheduleEdit(): void {
    this.reschedulingId.set(null);
    this.rowActionError.set(null);
  }

  protected confirmReschedule(appointment: AppointmentSummary): void {
    if (this.rescheduleForm.invalid || this.rowActionSubmitting()) {
      this.rescheduleForm.markAllAsTouched();
      return;
    }

    const durationMs = new Date(appointment.endAt).getTime() - new Date(appointment.startAt).getTime();
    const start = new Date(this.rescheduleForm.getRawValue().startAt);
    const end = new Date(start.getTime() + durationMs);

    this.rowActionSubmitting.set(true);
    this.rowActionError.set(null);

    this.schedulingService.reschedule(appointment.id, start.toISOString(), end.toISOString()).subscribe({
      next: (updated) => {
        this.replaceAppointment(updated);
        this.reschedulingId.set(null);
        this.rowActionSubmitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.rowActionSubmitting.set(false);
        if (error.error?.code === 'appointment_conflict') {
          this.rowActionError.set('Já existe um agendamento nesse horário.');
        } else {
          this.rowActionError.set('Não foi possível remarcar. Confira os dados informados.');
        }
      }
    });
  }

  protected startCancel(appointment: AppointmentSummary): void {
    this.reschedulingId.set(null);
    this.rowActionError.set(null);
    this.cancellingId.set(appointment.id);
    this.cancelForm.reset();
  }

  protected cancelCancelEdit(): void {
    this.cancellingId.set(null);
    this.rowActionError.set(null);
  }

  protected confirmCancel(appointment: AppointmentSummary): void {
    if (this.cancelForm.invalid || this.rowActionSubmitting()) {
      this.cancelForm.markAllAsTouched();
      return;
    }

    this.rowActionSubmitting.set(true);
    this.rowActionError.set(null);

    this.schedulingService.cancel(appointment.id, this.cancelForm.getRawValue().reason).subscribe({
      next: (updated) => {
        this.replaceAppointment(updated);
        this.cancellingId.set(null);
        this.rowActionSubmitting.set(false);
      },
      error: () => {
        this.rowActionSubmitting.set(false);
        this.rowActionError.set('Não foi possível cancelar o agendamento.');
      }
    });
  }

  private replaceAppointment(updated: AppointmentSummary): void {
    this.appointments.update((current) => current.map((item) => (item.id === updated.id ? updated : item)));
  }

  private reload(): void {
    this.clientsService.list().subscribe((clients) => this.clients.set(clients));
    this.catalogService.list().subscribe((services) => this.services.set(services.filter((s) => s.active)));

    this.loading.set(true);
    this.schedulingService.list().subscribe({
      next: (appointments) => {
        this.appointments.set(appointments);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}

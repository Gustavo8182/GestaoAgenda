import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/angular/daygrid';
import interactionPlugin from '@fullcalendar/angular/interaction';
import classicTheme from '@fullcalendar/angular/themes/classic';
import timeGridPlugin from '@fullcalendar/angular/timegrid';
import { Observable } from 'rxjs';
import type { CalendarOptions, DateClickInfo, EventClickInfo, EventInput } from 'fullcalendar';
import ptBrLocale from 'fullcalendar/locales/pt-br';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ServiceSummary } from '../../core/catalog/service-summary';
import { ClientSummary } from '../../core/clients/client-summary';
import { ClientsService } from '../../core/clients/clients.service';
import { AppointmentSummary } from '../../core/scheduling/appointment-summary';
import { SchedulingService } from '../../core/scheduling/scheduling.service';

type CalendarViewMode = 'list' | 'dayGridMonth' | 'timeGridWeek' | 'timeGridDay';

function resolveAppointmentErrorMessage(error: HttpErrorResponse, fallback: string): string {
  const code = error.error?.code;
  if (code === 'appointment_conflict') {
    return 'Já existe um agendamento nesse horário.';
  }
  if (code === 'blocked_time' || code === 'invalid_appointment') {
    return error.error?.message ?? fallback;
  }
  return fallback;
}

function formatLocalDateTimeInputValue(date: Date): string {
  const pad = (value: number) => value.toString().padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

function toLocalDateTimeInputValue(isoString: string): string {
  return formatLocalDateTimeInputValue(new Date(isoString));
}

const CALENDAR_EVENT_STATUS_SUFFIX: Partial<Record<AppointmentSummary['status'], string>> = {
  CANCELLED: 'Cancelado',
  NO_SHOW: 'Não compareceu',
  DONE: 'Realizado'
};

function calendarEventTitle(appointment: AppointmentSummary): string {
  const base = `${appointment.clientName} · ${appointment.serviceName}`;
  const suffix = CALENDAR_EVENT_STATUS_SUFFIX[appointment.status];
  return suffix ? `${base} (${suffix})` : base;
}

@Component({
  selector: 'app-agenda-page',
  imports: [ReactiveFormsModule, DatePipe, NgTemplateOutlet, FullCalendarModule],
  templateUrl: './agenda-page.component.html',
  styleUrl: './agenda-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgendaPageComponent {
  private readonly clientsService = inject(ClientsService);
  private readonly catalogService = inject(CatalogService);
  private readonly schedulingService = inject(SchedulingService);
  protected readonly authService = inject(AuthService);

  protected readonly exportUrl = `${environment.apiBaseUrl}/v1/reports/export/appointments`;

  protected readonly clients = signal<ClientSummary[]>([]);
  protected readonly services = signal<ServiceSummary[]>([]);
  protected readonly appointments = signal<AppointmentSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly reschedulingId = signal<string | null>(null);
  protected readonly cancellingId = signal<string | null>(null);
  protected readonly editingId = signal<string | null>(null);
  protected readonly rowActionError = signal<string | null>(null);
  protected readonly rowActionErrorId = signal<string | null>(null);
  protected readonly rowActionSubmitting = signal(false);

  protected readonly calendarView = signal<CalendarViewMode>('list');
  protected readonly selectedAppointmentId = signal<string | null>(null);

  protected readonly viewOptions: readonly { value: CalendarViewMode; label: string }[] = [
    { value: 'list', label: 'Lista' },
    { value: 'timeGridDay', label: 'Dia' },
    { value: 'timeGridWeek', label: 'Semana' },
    { value: 'dayGridMonth', label: 'Mês' }
  ];

  protected readonly selectedAppointment = computed(
    () => this.appointments().find((appointment) => appointment.id === this.selectedAppointmentId()) ?? null
  );

  protected readonly calendarEvents = computed<EventInput[]>(() =>
    this.appointments().map((appointment) => ({
      id: appointment.id,
      title: calendarEventTitle(appointment),
      start: appointment.startAt,
      end: appointment.endAt
    }))
  );

  protected readonly dayCalendarOptions = computed(() => this.buildCalendarOptions('timeGridDay'));
  protected readonly weekCalendarOptions = computed(() => this.buildCalendarOptions('timeGridWeek'));
  protected readonly monthCalendarOptions = computed(() => this.buildCalendarOptions('dayGridMonth'));

  protected readonly form = new FormGroup({
    clientId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    serviceId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    repeats: new FormControl(false, { nonNullable: true }),
    frequency: new FormControl<'WEEKLY' | 'BIWEEKLY'>('WEEKLY', { nonNullable: true }),
    occurrenceCount: new FormControl(4, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(2), Validators.max(52)]
    })
  });

  protected readonly rescheduleForm = new FormGroup({
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected readonly cancelForm = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected readonly editForm = new FormGroup({
    clientId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    serviceId: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    this.reload();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const { clientId, serviceId, startAt, repeats, frequency, occurrenceCount } = this.form.getRawValue();
    const service = this.services().find((item) => item.id === serviceId);
    if (!service) {
      this.errorMessage.set('Selecione um serviço válido.');
      return;
    }

    const start = new Date(startAt);
    const end = new Date(start.getTime() + service.durationMinutes * 60_000);

    this.submitting.set(true);
    this.errorMessage.set(null);

    const request: Observable<AppointmentSummary | AppointmentSummary[]> = repeats
      ? this.schedulingService.createRecurring(
          clientId,
          serviceId,
          start.toISOString(),
          end.toISOString(),
          frequency,
          occurrenceCount
        )
      : this.schedulingService.create(clientId, serviceId, start.toISOString(), end.toISOString());

    request.subscribe({
      next: (created: AppointmentSummary | AppointmentSummary[]) => {
        const createdAppointments = Array.isArray(created) ? created : [created];
        this.appointments.update((current) =>
          [...current, ...createdAppointments].sort((a, b) => a.startAt.localeCompare(b.startAt))
        );
        this.form.reset({ repeats: false, frequency: 'WEEKLY', occurrenceCount: 4 });
        this.submitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          resolveAppointmentErrorMessage(error, 'Não foi possível criar o agendamento. Confira os dados informados.')
        );
      }
    });
  }

  protected startReschedule(appointment: AppointmentSummary): void {
    this.cancellingId.set(null);
    this.editingId.set(null);
    this.clearRowError();
    this.reschedulingId.set(appointment.id);
    this.rescheduleForm.setValue({ startAt: toLocalDateTimeInputValue(appointment.startAt) });
  }

  protected cancelRescheduleEdit(): void {
    this.reschedulingId.set(null);
    this.clearRowError();
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
    this.clearRowError();

    this.schedulingService.reschedule(appointment.id, start.toISOString(), end.toISOString()).subscribe({
      next: (updated) => {
        this.replaceAppointment(updated);
        this.reschedulingId.set(null);
        this.rowActionSubmitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.rowActionSubmitting.set(false);
        this.setRowError(
          appointment.id,
          resolveAppointmentErrorMessage(error, 'Não foi possível remarcar. Confira os dados informados.')
        );
      }
    });
  }

  protected startEdit(appointment: AppointmentSummary): void {
    this.reschedulingId.set(null);
    this.cancellingId.set(null);
    this.clearRowError();
    this.editingId.set(appointment.id);
    this.editForm.setValue({ clientId: appointment.clientId, serviceId: appointment.serviceId });
  }

  protected cancelEditEdit(): void {
    this.editingId.set(null);
    this.clearRowError();
  }

  protected confirmEdit(appointment: AppointmentSummary): void {
    if (this.editForm.invalid || this.rowActionSubmitting()) {
      this.editForm.markAllAsTouched();
      return;
    }

    const { clientId, serviceId } = this.editForm.getRawValue();

    this.rowActionSubmitting.set(true);
    this.clearRowError();

    this.schedulingService.edit(appointment.id, clientId, serviceId).subscribe({
      next: (updated) => {
        this.replaceAppointment(updated);
        this.editingId.set(null);
        this.rowActionSubmitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.rowActionSubmitting.set(false);
        this.setRowError(
          appointment.id,
          resolveAppointmentErrorMessage(error, 'Não foi possível editar. Confira os dados informados.')
        );
      }
    });
  }

  protected startCancel(appointment: AppointmentSummary): void {
    this.reschedulingId.set(null);
    this.editingId.set(null);
    this.clearRowError();
    this.cancellingId.set(appointment.id);
    this.cancelForm.reset();
  }

  protected cancelCancelEdit(): void {
    this.cancellingId.set(null);
    this.clearRowError();
  }

  protected confirmCancel(appointment: AppointmentSummary): void {
    if (this.cancelForm.invalid || this.rowActionSubmitting()) {
      this.cancelForm.markAllAsTouched();
      return;
    }

    this.rowActionSubmitting.set(true);
    this.clearRowError();

    this.schedulingService.cancel(appointment.id, this.cancelForm.getRawValue().reason).subscribe({
      next: (updated) => {
        this.replaceAppointment(updated);
        this.cancellingId.set(null);
        this.rowActionSubmitting.set(false);
      },
      error: () => {
        this.rowActionSubmitting.set(false);
        this.setRowError(appointment.id, 'Não foi possível cancelar o agendamento.');
      }
    });
  }

  protected confirmAppointment(appointment: AppointmentSummary): void {
    this.runQuickAction(appointment, this.schedulingService.confirm(appointment.id), 'confirmar o agendamento');
  }

  protected registerArrival(appointment: AppointmentSummary): void {
    this.runQuickAction(appointment, this.schedulingService.registerArrival(appointment.id), 'registrar a chegada');
  }

  protected startService(appointment: AppointmentSummary): void {
    this.runQuickAction(appointment, this.schedulingService.startService(appointment.id), 'iniciar o atendimento');
  }

  protected completeService(appointment: AppointmentSummary): void {
    this.runQuickAction(appointment, this.schedulingService.complete(appointment.id), 'concluir o atendimento');
  }

  protected markNoShow(appointment: AppointmentSummary): void {
    this.runQuickAction(appointment, this.schedulingService.markNoShow(appointment.id), 'registrar a falta');
  }

  private runQuickAction(
    appointment: AppointmentSummary,
    request: Observable<AppointmentSummary>,
    actionDescription: string
  ): void {
    this.clearRowError();
    request.subscribe({
      next: (updated) => this.replaceAppointment(updated),
      error: () => this.setRowError(appointment.id, `Não foi possível ${actionDescription}.`)
    });
  }

  private setRowError(appointmentId: string, message: string): void {
    this.rowActionErrorId.set(appointmentId);
    this.rowActionError.set(message);
  }

  private clearRowError(): void {
    this.rowActionErrorId.set(null);
    this.rowActionError.set(null);
  }

  private replaceAppointment(updated: AppointmentSummary): void {
    this.appointments.update((current) => current.map((item) => (item.id === updated.id ? updated : item)));
  }

  private buildCalendarOptions(initialView: 'dayGridMonth' | 'timeGridWeek' | 'timeGridDay'): CalendarOptions {
    return {
      plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin, classicTheme],
      initialView,
      locale: ptBrLocale,
      headerToolbar: { left: 'prev,next today', center: 'title', right: '' },
      height: 'auto',
      nowIndicator: true,
      allDaySlot: false,
      slotMinTime: '07:00:00',
      slotMaxTime: '21:00:00',
      events: this.calendarEvents(),
      eventClick: (info: EventClickInfo) => this.selectedAppointmentId.set(info.event.id),
      dateClick: (info: DateClickInfo) => {
        if (!info.allDay) {
          this.form.patchValue({ startAt: formatLocalDateTimeInputValue(info.date) });
        }
      }
    };
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

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

  protected readonly form = new FormGroup({
    clientId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    serviceId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] })
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

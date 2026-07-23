import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ServiceSummary } from '../../core/catalog/service-summary';
import { ClientSummary } from '../../core/clients/client-summary';
import { ClientsService } from '../../core/clients/clients.service';
import { WaitlistSummary } from '../../core/waitlist/waitlist-summary';
import { WaitlistService } from '../../core/waitlist/waitlist.service';

function resolveConversionErrorMessage(error: HttpErrorResponse): string {
  return error.error?.message ?? 'Não foi possível converter em agendamento. Confira o horário escolhido.';
}

function toLocalDateTimeInputValue(date: Date): string {
  const pad = (value: number) => value.toString().padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

@Component({
  selector: 'app-waitlist-page',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './waitlist-page.component.html',
  styleUrl: './waitlist-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WaitlistPageComponent {
  private readonly clientsService = inject(ClientsService);
  private readonly catalogService = inject(CatalogService);
  private readonly waitlistService = inject(WaitlistService);

  protected readonly exportUrl = `${environment.apiBaseUrl}/v1/reports/export/waitlist`;

  protected readonly clients = signal<ClientSummary[]>([]);
  protected readonly services = signal<ServiceSummary[]>([]);
  protected readonly entries = signal<WaitlistSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly convertingId = signal<string | null>(null);
  protected readonly rowActionError = signal<string | null>(null);
  protected readonly rowActionErrorId = signal<string | null>(null);
  protected readonly rowActionSubmitting = signal(false);

  protected readonly form = new FormGroup({
    clientId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    serviceId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    preferredStartDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    preferredEndDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    preferredStartTime: new FormControl('09:00', { nonNullable: true, validators: [Validators.required] }),
    preferredEndTime: new FormControl('18:00', { nonNullable: true, validators: [Validators.required] }),
    priority: new FormControl<'LOW' | 'NORMAL' | 'HIGH'>('NORMAL', { nonNullable: true }),
    expiresAt: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected readonly convertForm = new FormGroup({
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  protected readonly indicators = computed(() => {
    const entries = this.entries();
    return {
      waiting: entries.filter((entry) => entry.status === 'WAITING').length,
      expired: entries.filter((entry) => entry.status === 'EXPIRED').length,
      converted: entries.filter((entry) => entry.status === 'CONVERTED').length,
      cancelled: entries.filter((entry) => entry.status === 'CANCELLED').length
    };
  });

  constructor() {
    this.reload();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const {
      clientId,
      serviceId,
      preferredStartDate,
      preferredEndDate,
      preferredStartTime,
      preferredEndTime,
      priority,
      expiresAt
    } = this.form.getRawValue();

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.waitlistService
      .create(
        clientId,
        serviceId,
        preferredStartDate,
        preferredEndDate,
        `${preferredStartTime}:00`,
        `${preferredEndTime}:00`,
        priority,
        new Date(expiresAt).toISOString()
      )
      .subscribe({
        next: (entry) => {
          this.entries.update((current) => [...current, entry]);
          this.form.reset({ priority: 'NORMAL', preferredStartTime: '09:00', preferredEndTime: '18:00' });
          this.submitting.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(
            error.error?.message ?? 'Não foi possível cadastrar. Confira os dados informados.'
          );
        }
      });
  }

  protected cancelEntry(entry: WaitlistSummary): void {
    this.clearRowError();
    this.waitlistService.cancel(entry.id).subscribe({
      next: (updated) => this.replaceEntry(updated),
      error: () => this.setRowError(entry.id, 'Não foi possível cancelar este registro.')
    });
  }

  protected startConvert(entry: WaitlistSummary): void {
    this.clearRowError();
    this.convertingId.set(entry.id);

    const defaultStart = new Date();
    defaultStart.setMinutes(0, 0, 0);
    defaultStart.setHours(defaultStart.getHours() + 1);
    this.convertForm.setValue({ startAt: toLocalDateTimeInputValue(defaultStart) });
  }

  protected cancelConvertEdit(): void {
    this.convertingId.set(null);
    this.clearRowError();
  }

  protected confirmConvert(entry: WaitlistSummary): void {
    if (this.convertForm.invalid || this.rowActionSubmitting()) {
      this.convertForm.markAllAsTouched();
      return;
    }

    const service = this.services().find((item) => item.id === entry.serviceId);
    if (!service) {
      this.setRowError(entry.id, 'Serviço não encontrado.');
      return;
    }

    const start = new Date(this.convertForm.getRawValue().startAt);
    const end = new Date(start.getTime() + service.durationMinutes * 60_000);

    this.rowActionSubmitting.set(true);
    this.clearRowError();

    this.waitlistService.convert(entry.id, start.toISOString(), end.toISOString()).subscribe({
      next: () => {
        this.rowActionSubmitting.set(false);
        this.convertingId.set(null);
        this.reload();
      },
      error: (error: HttpErrorResponse) => {
        this.rowActionSubmitting.set(false);
        this.setRowError(entry.id, resolveConversionErrorMessage(error));
      }
    });
  }

  private replaceEntry(updated: WaitlistSummary): void {
    this.entries.update((current) => current.map((item) => (item.id === updated.id ? updated : item)));
  }

  private setRowError(entryId: string, message: string): void {
    this.rowActionErrorId.set(entryId);
    this.rowActionError.set(message);
  }

  private clearRowError(): void {
    this.rowActionErrorId.set(null);
    this.rowActionError.set(null);
  }

  private reload(): void {
    this.clientsService.list().subscribe((clients) => this.clients.set(clients));
    this.catalogService.list().subscribe((services) => this.services.set(services.filter((s) => s.active)));

    this.loading.set(true);
    this.waitlistService.list().subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}

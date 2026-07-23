import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientSummary } from '../../core/clients/client-summary';
import { ClientsService } from '../../core/clients/clients.service';
import { AppointmentSummary } from '../../core/scheduling/appointment-summary';
import { SchedulingService } from '../../core/scheduling/scheduling.service';

@Component({
  selector: 'app-clients-page',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './clients-page.component.html',
  styleUrl: './clients-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClientsPageComponent {
  private readonly clientsService = inject(ClientsService);
  private readonly schedulingService = inject(SchedulingService);

  protected readonly exportUrl = `${environment.apiBaseUrl}/v1/reports/export/clients`;

  protected readonly clients = signal<ClientSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly duplicateWarning = signal(false);

  protected readonly expandedClientId = signal<string | null>(null);
  protected readonly historyLoading = signal(false);
  protected readonly history = signal<AppointmentSummary[]>([]);

  protected readonly searchControl = new FormControl('', { nonNullable: true });

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    alternatePhone: new FormControl('', { nonNullable: true }),
    origin: new FormControl('', { nonNullable: true }),
    notes: new FormControl('', { nonNullable: true })
  });

  constructor() {
    this.reload();
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe((query) => this.reload(query));
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.duplicateWarning.set(false);

    const { name, phone, alternatePhone, origin, notes } = this.form.getRawValue();

    this.clientsService.create(name, phone, alternatePhone, origin, notes).subscribe({
      next: (result) => {
        this.clients.update((current) =>
          [...current, result.client].sort((a, b) => a.name.localeCompare(b.name))
        );
        this.duplicateWarning.set(result.possibleDuplicate);
        this.form.reset();
        this.submitting.set(false);
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('Não foi possível cadastrar a cliente. Confira os dados informados.');
      }
    });
  }

  protected toggleHistory(client: ClientSummary): void {
    if (this.expandedClientId() === client.id) {
      this.expandedClientId.set(null);
      return;
    }

    this.expandedClientId.set(client.id);
    this.history.set([]);
    this.historyLoading.set(true);

    this.schedulingService.listByClient(client.id).subscribe({
      next: (history) => {
        this.history.set(history);
        this.historyLoading.set(false);
      },
      error: () => this.historyLoading.set(false)
    });
  }

  private reload(query?: string): void {
    this.loading.set(true);
    this.clientsService.list(query).subscribe({
      next: (clients) => {
        this.clients.set(clients);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}

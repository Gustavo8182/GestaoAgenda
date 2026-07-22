import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ClientSummary } from '../../core/clients/client-summary';
import { ClientsService } from '../../core/clients/clients.service';

@Component({
  selector: 'app-clients-page',
  imports: [ReactiveFormsModule],
  templateUrl: './clients-page.component.html',
  styleUrl: './clients-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClientsPageComponent {
  private readonly clientsService = inject(ClientsService);

  protected readonly clients = signal<ClientSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly duplicateWarning = signal(false);

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

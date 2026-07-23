import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ServiceSummary } from '../../core/catalog/service-summary';

const DEFAULT_COLOR = '#94a3b8';

@Component({
  selector: 'app-services-page',
  imports: [ReactiveFormsModule],
  templateUrl: './services-page.component.html',
  styleUrl: './services-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServicesPageComponent {
  private readonly catalogService = inject(CatalogService);
  protected readonly authService = inject(AuthService);

  protected readonly services = signal<ServiceSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly deactivatingId = signal<string | null>(null);

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)]
    }),
    color: new FormControl(DEFAULT_COLOR, { nonNullable: true }),
    displayOrder: new FormControl<number | null>(null),
    requiresConfirmation: new FormControl(false, { nonNullable: true })
  });

  constructor() {
    this.reload();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { name, durationMinutes, color, displayOrder, requiresConfirmation } = this.form.getRawValue();

    this.catalogService
      .create(name, durationMinutes!, color, displayOrder ?? undefined, requiresConfirmation)
      .subscribe({
        next: (service) => {
          this.services.update((current) => [...current, service].sort(byDisplayOrderThenName));
          this.form.reset({ color: DEFAULT_COLOR, requiresConfirmation: false });
          this.submitting.set(false);
        },
        error: () => {
          this.submitting.set(false);
          this.errorMessage.set('Não foi possível cadastrar o serviço. Confira os dados informados.');
        }
      });
  }

  protected deactivate(serviceId: string): void {
    if (this.deactivatingId()) {
      return;
    }

    this.deactivatingId.set(serviceId);
    this.catalogService.deactivate(serviceId).subscribe({
      next: (updated) => {
        this.services.update((current) => current.map((s) => (s.id === updated.id ? updated : s)));
        this.deactivatingId.set(null);
      },
      error: () => {
        this.deactivatingId.set(null);
      }
    });
  }

  private reload(): void {
    this.loading.set(true);
    this.catalogService.list().subscribe({
      next: (services) => {
        this.services.set([...services].sort(byDisplayOrderThenName));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}

function byDisplayOrderThenName(a: ServiceSummary, b: ServiceSummary): number {
  return a.displayOrder - b.displayOrder || a.name.localeCompare(b.name);
}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
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
  protected readonly togglingActiveId = signal<string | null>(null);
  protected readonly editingId = signal<string | null>(null);
  protected readonly rowActionError = signal<string | null>(null);
  protected readonly rowActionSubmitting = signal(false);

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)]
    }),
    color: new FormControl(DEFAULT_COLOR, { nonNullable: true }),
    displayOrder: new FormControl<number | null>(null),
    requiresConfirmation: new FormControl(false, { nonNullable: true }),
    bufferMinutes: new FormControl<number | null>(null, { validators: [Validators.min(0)] })
  });

  protected readonly editForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)]
    }),
    color: new FormControl(DEFAULT_COLOR, { nonNullable: true }),
    displayOrder: new FormControl<number | null>(null, { validators: [Validators.required] }),
    requiresConfirmation: new FormControl(false, { nonNullable: true }),
    bufferMinutes: new FormControl<number | null>(null, { validators: [Validators.min(0)] })
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

    const { name, durationMinutes, color, displayOrder, requiresConfirmation, bufferMinutes } =
      this.form.getRawValue();

    this.catalogService
      .create(
        name,
        durationMinutes!,
        color,
        displayOrder ?? undefined,
        requiresConfirmation,
        bufferMinutes ?? undefined
      )
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

  protected startEdit(service: ServiceSummary): void {
    this.editingId.set(service.id);
    this.rowActionError.set(null);
    this.editForm.setValue({
      name: service.name,
      durationMinutes: service.durationMinutes,
      color: service.color ?? DEFAULT_COLOR,
      displayOrder: service.displayOrder,
      requiresConfirmation: service.requiresConfirmation,
      bufferMinutes: service.bufferMinutes || null
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.rowActionError.set(null);
  }

  protected confirmEdit(serviceId: string): void {
    if (this.editForm.invalid || this.rowActionSubmitting()) {
      this.editForm.markAllAsTouched();
      return;
    }

    const { name, durationMinutes, color, displayOrder, requiresConfirmation, bufferMinutes } =
      this.editForm.getRawValue();

    this.rowActionSubmitting.set(true);
    this.rowActionError.set(null);

    this.catalogService
      .edit(serviceId, name, durationMinutes!, color, displayOrder!, requiresConfirmation, bufferMinutes ?? undefined)
      .subscribe({
        next: (updated) => {
          this.services.update((current) =>
            current.map((s) => (s.id === updated.id ? updated : s)).sort(byDisplayOrderThenName)
          );
          this.editingId.set(null);
          this.rowActionSubmitting.set(false);
        },
        error: () => {
          this.rowActionSubmitting.set(false);
          this.rowActionError.set('Não foi possível salvar as alterações. Confira os dados informados.');
        }
      });
  }

  protected deactivate(serviceId: string): void {
    this.toggleActive(serviceId, this.catalogService.deactivate(serviceId));
  }

  protected reactivate(serviceId: string): void {
    this.toggleActive(serviceId, this.catalogService.reactivate(serviceId));
  }

  private toggleActive(serviceId: string, request: Observable<ServiceSummary>): void {
    if (this.togglingActiveId()) {
      return;
    }

    this.togglingActiveId.set(serviceId);
    request.subscribe({
      next: (updated) => {
        this.services.update((current) => current.map((s) => (s.id === updated.id ? updated : s)));
        this.togglingActiveId.set(null);
      },
      error: () => {
        this.togglingActiveId.set(null);
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

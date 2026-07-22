import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ServiceSummary } from '../../core/catalog/service-summary';

@Component({
  selector: 'app-services-page',
  imports: [ReactiveFormsModule],
  templateUrl: './services-page.component.html',
  styleUrl: './services-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServicesPageComponent {
  private readonly catalogService = inject(CatalogService);

  protected readonly services = signal<ServiceSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)]
    })
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

    const { name, durationMinutes } = this.form.getRawValue();

    this.catalogService.create(name, durationMinutes!).subscribe({
      next: (service) => {
        this.services.update((current) =>
          [...current, service].sort((a, b) => a.name.localeCompare(b.name))
        );
        this.form.reset();
        this.submitting.set(false);
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('Não foi possível cadastrar o serviço. Confira o nome e a duração.');
      }
    });
  }

  private reload(): void {
    this.loading.set(true);
    this.catalogService.list().subscribe({
      next: (services) => {
        this.services.set(services);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}

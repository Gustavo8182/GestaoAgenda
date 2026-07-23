import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AvailabilityService } from '../../core/availability/availability.service';
import { BlockSummary } from '../../core/availability/block-summary';
import { BusinessHoursEntry, DayOfWeek } from '../../core/availability/business-hours-entry';

interface HoursRow {
  day: DayOfWeek;
  label: string;
  enabled: boolean;
  startTime: string;
  endTime: string;
}

const DAYS: readonly { day: DayOfWeek; label: string }[] = [
  { day: 'MONDAY', label: 'Segunda-feira' },
  { day: 'TUESDAY', label: 'Terça-feira' },
  { day: 'WEDNESDAY', label: 'Quarta-feira' },
  { day: 'THURSDAY', label: 'Quinta-feira' },
  { day: 'FRIDAY', label: 'Sexta-feira' },
  { day: 'SATURDAY', label: 'Sábado' },
  { day: 'SUNDAY', label: 'Domingo' }
];

@Component({
  selector: 'app-settings-page',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './settings-page.component.html',
  styleUrl: './settings-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SettingsPageComponent {
  private readonly availabilityService = inject(AvailabilityService);

  protected readonly hoursRows = signal<HoursRow[]>(
    DAYS.map(({ day, label }) => ({ day, label, enabled: false, startTime: '09:00', endTime: '18:00' }))
  );
  protected readonly hoursSaving = signal(false);
  protected readonly hoursError = signal<string | null>(null);
  protected readonly hoursSaved = signal(false);

  protected readonly blocks = signal<BlockSummary[]>([]);
  protected readonly blockError = signal<string | null>(null);
  protected readonly blockSubmitting = signal(false);

  protected readonly blockForm = new FormGroup({
    startAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    endAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    this.availabilityService.listBusinessHours().subscribe((entries) => {
      this.hoursRows.update((rows) =>
        rows.map((row) => {
          const configured = entries.find((entry) => entry.dayOfWeek === row.day);
          return configured
            ? {
                ...row,
                enabled: true,
                startTime: configured.startTime.slice(0, 5),
                endTime: configured.endTime.slice(0, 5)
              }
            : row;
        })
      );
    });

    this.availabilityService.listBlocks().subscribe((blocks) => this.blocks.set(blocks));
  }

  protected toggleDay(day: DayOfWeek, enabled: boolean): void {
    this.hoursRows.update((rows) => rows.map((row) => (row.day === day ? { ...row, enabled } : row)));
  }

  protected updateTime(day: DayOfWeek, field: 'startTime' | 'endTime', value: string): void {
    this.hoursRows.update((rows) => rows.map((row) => (row.day === day ? { ...row, [field]: value } : row)));
  }

  protected saveBusinessHours(): void {
    this.hoursSaving.set(true);
    this.hoursError.set(null);
    this.hoursSaved.set(false);

    const entries: BusinessHoursEntry[] = this.hoursRows()
      .filter((row) => row.enabled)
      .map((row) => ({ dayOfWeek: row.day, startTime: `${row.startTime}:00`, endTime: `${row.endTime}:00` }));

    this.availabilityService.replaceBusinessHours(entries).subscribe({
      next: () => {
        this.hoursSaving.set(false);
        this.hoursSaved.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.hoursSaving.set(false);
        this.hoursError.set(
          error.error?.message ?? 'Não foi possível salvar os horários. Confira os intervalos informados.'
        );
      }
    });
  }

  protected submitBlock(): void {
    if (this.blockForm.invalid || this.blockSubmitting()) {
      this.blockForm.markAllAsTouched();
      return;
    }

    const { startAt, endAt, reason } = this.blockForm.getRawValue();
    const start = new Date(startAt);
    const end = new Date(endAt);

    this.blockSubmitting.set(true);
    this.blockError.set(null);

    this.availabilityService.createBlock(start.toISOString(), end.toISOString(), reason).subscribe({
      next: (block) => {
        this.blocks.update((current) => [...current, block].sort((a, b) => a.startAt.localeCompare(b.startAt)));
        this.blockForm.reset();
        this.blockSubmitting.set(false);
      },
      error: () => {
        this.blockSubmitting.set(false);
        this.blockError.set('Não foi possível criar o bloqueio. Confira os dados informados.');
      }
    });
  }

  protected removeBlock(blockId: string): void {
    this.availabilityService.removeBlock(blockId).subscribe(() => {
      this.blocks.update((current) => current.filter((block) => block.id !== blockId));
    });
  }
}

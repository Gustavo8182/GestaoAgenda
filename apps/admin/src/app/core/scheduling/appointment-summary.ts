export interface AppointmentSummary {
  readonly id: string;
  readonly clientName: string;
  readonly serviceName: string;
  readonly startAt: string;
  readonly endAt: string;
  readonly status: 'SCHEDULED' | 'CONFIRMED' | 'ARRIVED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED' | 'NO_SHOW';
  readonly cancellationReason: string | null;
  readonly seriesId: string | null;
}

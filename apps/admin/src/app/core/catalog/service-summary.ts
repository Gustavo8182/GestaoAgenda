export interface ServiceSummary {
  readonly id: string;
  readonly name: string;
  readonly durationMinutes: number;
  readonly color: string | null;
  readonly displayOrder: number;
  readonly requiresConfirmation: boolean;
  readonly active: boolean;
  readonly bufferMinutes: number;
}

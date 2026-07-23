export interface WaitlistSummary {
  readonly id: string;
  readonly serviceId: string;
  readonly clientName: string;
  readonly serviceName: string;
  readonly preferredStartDate: string;
  readonly preferredEndDate: string;
  readonly preferredStartTime: string;
  readonly preferredEndTime: string;
  readonly priority: 'LOW' | 'NORMAL' | 'HIGH';
  readonly expiresAt: string;
  readonly status: 'WAITING' | 'EXPIRED' | 'CONVERTED' | 'CANCELLED';
  readonly appointmentId: string | null;
}

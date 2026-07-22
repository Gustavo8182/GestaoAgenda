import { AppointmentSummary } from '../scheduling/appointment-summary';
import { BlockSummary } from '../availability/block-summary';

export interface WeekSummary {
  readonly scheduledCount: number;
  readonly cancelledCount: number;
}

export interface DashboardSummary {
  readonly todayAppointments: AppointmentSummary[];
  readonly nextAppointment: AppointmentSummary | null;
  readonly todayBlocks: BlockSummary[];
  readonly week: WeekSummary;
}

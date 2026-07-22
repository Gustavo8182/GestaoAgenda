export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface BusinessHoursEntry {
  readonly dayOfWeek: DayOfWeek;
  readonly startTime: string;
  readonly endTime: string;
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppointmentSummary } from './appointment-summary';

@Injectable({ providedIn: 'root' })
export class SchedulingService {
  private readonly http = inject(HttpClient);

  list(): Observable<AppointmentSummary[]> {
    return this.http.get<AppointmentSummary[]>(`${environment.apiBaseUrl}/v1/appointments`);
  }

  create(clientId: string, serviceId: string, startAt: string, endAt: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments`, {
      clientId,
      serviceId,
      startAt,
      endAt
    });
  }

  reschedule(appointmentId: string, startAt: string, endAt: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(
      `${environment.apiBaseUrl}/v1/appointments/${appointmentId}/reschedule`,
      { startAt, endAt }
    );
  }

  cancel(appointmentId: string, reason: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/cancel`, {
      reason
    });
  }
}

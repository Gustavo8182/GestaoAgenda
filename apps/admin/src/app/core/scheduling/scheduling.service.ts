import { HttpClient, HttpParams } from '@angular/common/http';
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

  listByClient(clientId: string): Observable<AppointmentSummary[]> {
    return this.http.get<AppointmentSummary[]>(`${environment.apiBaseUrl}/v1/appointments`, {
      params: new HttpParams().set('clientId', clientId)
    });
  }

  create(clientId: string, serviceId: string, startAt: string, endAt: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments`, {
      clientId,
      serviceId,
      startAt,
      endAt
    });
  }

  createRecurring(
    clientId: string,
    serviceId: string,
    startAt: string,
    endAt: string,
    frequency: 'WEEKLY' | 'BIWEEKLY',
    occurrenceCount: number
  ): Observable<AppointmentSummary[]> {
    return this.http.post<AppointmentSummary[]>(`${environment.apiBaseUrl}/v1/appointments/recurring`, {
      clientId,
      serviceId,
      startAt,
      endAt,
      frequency,
      occurrenceCount
    });
  }

  reschedule(appointmentId: string, startAt: string, endAt: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(
      `${environment.apiBaseUrl}/v1/appointments/${appointmentId}/reschedule`,
      { startAt, endAt }
    );
  }

  edit(appointmentId: string, clientId: string, serviceId: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/edit`, {
      clientId,
      serviceId
    });
  }

  cancel(appointmentId: string, reason: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/cancel`, {
      reason
    });
  }

  confirm(appointmentId: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/confirm`, {});
  }

  registerArrival(appointmentId: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/arrive`, {});
  }

  startService(appointmentId: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/start`, {});
  }

  complete(appointmentId: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/complete`, {});
  }

  markNoShow(appointmentId: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/appointments/${appointmentId}/no-show`, {});
  }
}

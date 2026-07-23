import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppointmentSummary } from '../scheduling/appointment-summary';
import { WaitlistSummary } from './waitlist-summary';

@Injectable({ providedIn: 'root' })
export class WaitlistService {
  private readonly http = inject(HttpClient);

  list(): Observable<WaitlistSummary[]> {
    return this.http.get<WaitlistSummary[]>(`${environment.apiBaseUrl}/v1/waitlist`);
  }

  create(
    clientId: string,
    serviceId: string,
    preferredStartDate: string,
    preferredEndDate: string,
    preferredStartTime: string,
    preferredEndTime: string,
    priority: 'LOW' | 'NORMAL' | 'HIGH',
    expiresAt: string
  ): Observable<WaitlistSummary> {
    return this.http.post<WaitlistSummary>(`${environment.apiBaseUrl}/v1/waitlist`, {
      clientId,
      serviceId,
      preferredStartDate,
      preferredEndDate,
      preferredStartTime,
      preferredEndTime,
      priority,
      expiresAt
    });
  }

  cancel(entryId: string): Observable<WaitlistSummary> {
    return this.http.post<WaitlistSummary>(`${environment.apiBaseUrl}/v1/waitlist/${entryId}/cancel`, {});
  }

  convert(entryId: string, startAt: string, endAt: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/waitlist/${entryId}/convert`, {
      startAt,
      endAt
    });
  }

  findCompatible(serviceId: string, startAt: string, endAt: string): Observable<WaitlistSummary[]> {
    return this.http.get<WaitlistSummary[]>(`${environment.apiBaseUrl}/v1/waitlist/compatible`, {
      params: new HttpParams().set('serviceId', serviceId).set('startAt', startAt).set('endAt', endAt)
    });
  }
}

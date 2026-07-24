import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppointmentSummary } from '../scheduling/appointment-summary';
import { AssignableMember, RelationshipStatus, RelationshipSummary } from './relationship-summary';

@Injectable({ providedIn: 'root' })
export class RelationshipsService {
  private readonly http = inject(HttpClient);

  list(): Observable<RelationshipSummary[]> {
    return this.http.get<RelationshipSummary[]>(`${environment.apiBaseUrl}/v1/relationships`);
  }

  listAssignableMembers(): Observable<AssignableMember[]> {
    return this.http.get<AssignableMember[]>(`${environment.apiBaseUrl}/v1/relationships/assignable-members`);
  }

  reassign(contactId: string, responsibleUserId: string): Observable<RelationshipSummary> {
    return this.http.post<RelationshipSummary>(`${environment.apiBaseUrl}/v1/relationships/${contactId}/reassign`, {
      responsibleUserId
    });
  }

  create(name: string, phone: string, origin?: string): Observable<RelationshipSummary> {
    return this.http.post<RelationshipSummary>(`${environment.apiBaseUrl}/v1/relationships`, {
      name,
      phone,
      origin: origin || null
    });
  }

  update(
    contactId: string,
    status: RelationshipStatus | null,
    nextAction: string | null,
    nextActionAt: string | null
  ): Observable<RelationshipSummary> {
    return this.http.post<RelationshipSummary>(`${environment.apiBaseUrl}/v1/relationships/${contactId}/update`, {
      status,
      nextAction,
      nextActionAt
    });
  }

  convert(contactId: string, serviceId: string, startAt: string, endAt: string): Observable<AppointmentSummary> {
    return this.http.post<AppointmentSummary>(`${environment.apiBaseUrl}/v1/relationships/${contactId}/convert`, {
      serviceId,
      startAt,
      endAt
    });
  }
}

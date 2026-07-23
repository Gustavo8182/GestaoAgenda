import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientSummary, CreateClientResult } from './client-summary';

@Injectable({ providedIn: 'root' })
export class ClientsService {
  private readonly http = inject(HttpClient);

  list(query?: string): Observable<ClientSummary[]> {
    const params = query ? new HttpParams().set('query', query) : undefined;
    return this.http.get<ClientSummary[]>(`${environment.apiBaseUrl}/v1/clients`, { params });
  }

  create(
    name: string,
    phone: string,
    alternatePhone?: string,
    origin?: string,
    notes?: string
  ): Observable<CreateClientResult> {
    return this.http.post<CreateClientResult>(`${environment.apiBaseUrl}/v1/clients`, {
      name,
      phone,
      alternatePhone: alternatePhone || null,
      origin: origin || null,
      notes: notes || null
    });
  }

  restrictContact(clientId: string, reason?: string): Observable<ClientSummary> {
    return this.http.post<ClientSummary>(`${environment.apiBaseUrl}/v1/clients/${clientId}/restrict-contact`, {
      reason: reason || null
    });
  }

  liftContactRestriction(clientId: string): Observable<ClientSummary> {
    return this.http.post<ClientSummary>(
      `${environment.apiBaseUrl}/v1/clients/${clientId}/lift-contact-restriction`,
      {}
    );
  }
}

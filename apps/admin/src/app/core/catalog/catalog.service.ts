import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ServiceSummary } from './service-summary';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  list(): Observable<ServiceSummary[]> {
    return this.http.get<ServiceSummary[]>(`${environment.apiBaseUrl}/v1/catalog/services`);
  }

  create(
    name: string,
    durationMinutes: number,
    color?: string,
    displayOrder?: number,
    requiresConfirmation = false
  ): Observable<ServiceSummary> {
    return this.http.post<ServiceSummary>(`${environment.apiBaseUrl}/v1/catalog/services`, {
      name,
      durationMinutes,
      color: color || null,
      displayOrder: displayOrder ?? null,
      requiresConfirmation
    });
  }

  deactivate(serviceId: string): Observable<ServiceSummary> {
    return this.http.post<ServiceSummary>(
      `${environment.apiBaseUrl}/v1/catalog/services/${serviceId}/deactivate`,
      {}
    );
  }
}

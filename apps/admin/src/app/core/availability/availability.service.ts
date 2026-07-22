import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BlockSummary } from './block-summary';
import { BusinessHoursEntry } from './business-hours-entry';

@Injectable({ providedIn: 'root' })
export class AvailabilityService {
  private readonly http = inject(HttpClient);

  listBusinessHours(): Observable<BusinessHoursEntry[]> {
    return this.http.get<BusinessHoursEntry[]>(`${environment.apiBaseUrl}/v1/availability/business-hours`);
  }

  replaceBusinessHours(entries: BusinessHoursEntry[]): Observable<BusinessHoursEntry[]> {
    return this.http.put<BusinessHoursEntry[]>(`${environment.apiBaseUrl}/v1/availability/business-hours`, entries);
  }

  listBlocks(): Observable<BlockSummary[]> {
    return this.http.get<BlockSummary[]>(`${environment.apiBaseUrl}/v1/availability/blocks`);
  }

  createBlock(startAt: string, endAt: string, reason: string): Observable<BlockSummary> {
    return this.http.post<BlockSummary>(`${environment.apiBaseUrl}/v1/availability/blocks`, {
      startAt,
      endAt,
      reason
    });
  }

  removeBlock(blockId: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/v1/availability/blocks/${blockId}`);
  }
}

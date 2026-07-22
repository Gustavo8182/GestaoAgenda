import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditEntry } from './audit-entry';

@Injectable({ providedIn: 'root' })
export class AuditTrailService {
  private readonly http = inject(HttpClient);

  recent(): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${environment.apiBaseUrl}/v1/audit-log`);
  }
}

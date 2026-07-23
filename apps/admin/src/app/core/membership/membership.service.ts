import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MemberSummary } from './member-summary';

@Injectable({ providedIn: 'root' })
export class MembershipService {
  private readonly http = inject(HttpClient);

  list(): Observable<MemberSummary[]> {
    return this.http.get<MemberSummary[]>(`${environment.apiBaseUrl}/v1/organizations/members`);
  }

  invite(email: string, displayName: string): Observable<MemberSummary> {
    return this.http.post<MemberSummary>(`${environment.apiBaseUrl}/v1/organizations/members`, {
      email,
      displayName
    });
  }

  disable(memberId: string): Observable<MemberSummary> {
    return this.http.post<MemberSummary>(
      `${environment.apiBaseUrl}/v1/organizations/members/${memberId}/disable`,
      {}
    );
  }

  reactivate(memberId: string): Observable<MemberSummary> {
    return this.http.post<MemberSummary>(
      `${environment.apiBaseUrl}/v1/organizations/members/${memberId}/reactivate`,
      {}
    );
  }
}

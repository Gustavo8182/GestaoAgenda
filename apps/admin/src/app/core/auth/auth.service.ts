import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CurrentOrganization, CurrentUser, SessionInfo } from './current-user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly sessionSignal = signal<SessionInfo | null>(null);

  readonly currentUser = computed<CurrentUser | null>(() => this.sessionSignal()?.user ?? null);
  readonly currentOrganization = computed<CurrentOrganization | null>(
    () => this.sessionSignal()?.organization ?? null
  );
  readonly isAuthenticated = computed(() => this.sessionSignal() !== null);

  login(email: string, password: string): Observable<SessionInfo> {
    return this.http
      .post<SessionInfo>(`${environment.apiBaseUrl}/v1/auth/login`, { email, password })
      .pipe(tap((session) => this.sessionSignal.set(session)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${environment.apiBaseUrl}/v1/auth/logout`, {})
      .pipe(tap(() => this.sessionSignal.set(null)));
  }

  fetchCurrentUser(): Observable<SessionInfo | null> {
    return this.http.get<SessionInfo>(`${environment.apiBaseUrl}/v1/auth/me`).pipe(
      tap((session) => this.sessionSignal.set(session)),
      catchError(() => {
        this.sessionSignal.set(null);
        return of(null);
      })
    );
  }
}

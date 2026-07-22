import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientSummary, CreateClientResult } from './client-summary';

@Injectable({ providedIn: 'root' })
export class ClientsService {
  private readonly http = inject(HttpClient);

  list(): Observable<ClientSummary[]> {
    return this.http.get<ClientSummary[]>(`${environment.apiBaseUrl}/v1/clients`);
  }

  create(name: string, phone: string): Observable<CreateClientResult> {
    return this.http.post<CreateClientResult>(`${environment.apiBaseUrl}/v1/clients`, { name, phone });
  }
}

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaPageComponent } from './agenda-page.component';

async function createComponent(
  clients: unknown[],
  services: unknown[]
): Promise<{ fixture: ComponentFixture<AgendaPageComponent>; httpMock: HttpTestingController }> {
  await TestBed.configureTestingModule({
    imports: [AgendaPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AgendaPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/clients').flush(clients);
  httpMock.expectOne('/api/v1/catalog/services').flush(services);
  httpMock.expectOne('/api/v1/appointments').flush([]);

  return { fixture, httpMock };
}

describe('AgendaPageComponent', () => {
  it('shows a hint to register clients and services first when there are none', async () => {
    const { fixture, httpMock } = await createComponent([], []);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Cadastre clientes e serviços primeiro');
    httpMock.verify();
  });

  it('shows the empty state when there are clients and services but no appointments', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30 }]
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum agendamento cadastrado');
    httpMock.verify();
  });

  it('creates an appointment computing the end time from the service duration', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30 }]
    );
    fixture.detectChanges();

    const clientSelect: HTMLSelectElement = fixture.nativeElement.querySelector('#clientId');
    clientSelect.value = clientSelect.options[1].value;
    clientSelect.dispatchEvent(new Event('change'));

    const serviceSelect: HTMLSelectElement = fixture.nativeElement.querySelector('#serviceId');
    serviceSelect.value = serviceSelect.options[1].value;
    serviceSelect.dispatchEvent(new Event('change'));

    const startInput: HTMLInputElement = fixture.nativeElement.querySelector('#startAt');
    startInput.value = '2026-08-01T10:00';
    startInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/appointments');
    expect(request.request.body.clientId).toBe('c1');
    expect(request.request.body.serviceId).toBe('s1');
    expect(new Date(request.request.body.endAt).getTime() - new Date(request.request.body.startAt).getTime()).toBe(
      30 * 60_000
    );

    request.flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: request.request.body.startAt,
      endAt: request.request.body.endAt
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fulana de Tal');
    httpMock.verify();
  });

  it('shows a conflict message when the API rejects an overlapping appointment', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30 }]
    );
    fixture.detectChanges();

    const clientSelect: HTMLSelectElement = fixture.nativeElement.querySelector('#clientId');
    clientSelect.value = clientSelect.options[1].value;
    clientSelect.dispatchEvent(new Event('change'));

    const serviceSelect: HTMLSelectElement = fixture.nativeElement.querySelector('#serviceId');
    serviceSelect.value = serviceSelect.options[1].value;
    serviceSelect.dispatchEvent(new Event('change'));

    const startInput: HTMLInputElement = fixture.nativeElement.querySelector('#startAt');
    startInput.value = '2026-08-01T10:00';
    startInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/appointments');
    request.flush(
      { code: 'appointment_conflict', message: 'Já existe um agendamento nesse horário.' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Já existe um agendamento nesse horário.');
    httpMock.verify();
  });
});

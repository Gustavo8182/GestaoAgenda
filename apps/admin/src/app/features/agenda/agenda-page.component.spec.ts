import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgendaPageComponent } from './agenda-page.component';

async function createComponent(
  clients: unknown[],
  services: unknown[],
  appointments: unknown[] = []
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
  httpMock.expectOne('/api/v1/appointments').flush(appointments);

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
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }]
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum agendamento cadastrado');
    httpMock.verify();
  });

  it('creates an appointment computing the end time from the service duration', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }]
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
      endAt: request.request.body.endAt,
      status: 'SCHEDULED',
      cancellationReason: null
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fulana de Tal');
    httpMock.verify();
  });

  it('excludes inactive services from the create-appointment dropdown', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [
        { id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true },
        { id: 's2', name: 'Descontinuado', durationMinutes: 20, color: null, displayOrder: 1, requiresConfirmation: false, active: false }
      ]
    );
    fixture.detectChanges();

    const serviceSelect: HTMLSelectElement = fixture.nativeElement.querySelector('#serviceId');
    const optionLabels = Array.from(serviceSelect.options).map((option) => option.textContent?.trim());

    expect(optionLabels.some((label) => label?.includes('Corte'))).toBe(true);
    expect(optionLabels.some((label) => label?.includes('Descontinuado'))).toBe(false);
    httpMock.verify();
  });

  it('shows a conflict message when the API rejects an overlapping appointment', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }]
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

  it('reschedules an appointment preserving its original duration', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [
        {
          id: 'a1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          startAt: '2026-08-01T10:00:00Z',
          endAt: '2026-08-01T10:30:00Z',
          status: 'SCHEDULED',
          cancellationReason: null
        }
      ]
    );
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.link-button'));
    const rescheduleButton = buttons.find((button) => button.textContent?.trim() === 'Remarcar');
    rescheduleButton?.click();
    fixture.detectChanges();

    const startInput: HTMLInputElement = fixture.nativeElement.querySelector('input[formcontrolname="startAt"]');
    startInput.value = '2026-08-02T14:00';
    startInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.row-form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/appointments/a1/reschedule');
    expect(new Date(request.request.body.endAt).getTime() - new Date(request.request.body.startAt).getTime()).toBe(
      30 * 60_000
    );

    request.flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: request.request.body.startAt,
      endAt: request.request.body.endAt,
      status: 'SCHEDULED',
      cancellationReason: null
    });
    fixture.detectChanges();

    httpMock.verify();
  });

  it('cancels an appointment and shows the reason', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [
        {
          id: 'a1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          startAt: '2026-08-01T10:00:00Z',
          endAt: '2026-08-01T10:30:00Z',
          status: 'SCHEDULED',
          cancellationReason: null
        }
      ]
    );
    fixture.detectChanges();

    const cancelButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button--danger');
    cancelButton.click();
    fixture.detectChanges();

    const reasonInput: HTMLInputElement = fixture.nativeElement.querySelector('input[formcontrolname="reason"]');
    reasonInput.value = 'Cliente remarcou por telefone.';
    reasonInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.row-form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/appointments/a1/cancel');
    expect(request.request.body.reason).toBe('Cliente remarcou por telefone.');

    request.flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: '2026-08-01T10:00:00Z',
      endAt: '2026-08-01T10:30:00Z',
      status: 'CANCELLED',
      cancellationReason: 'Cliente remarcou por telefone.'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Cancelado — Cliente remarcou por telefone.');
    httpMock.verify();
  });

  it('confirms an appointment and updates the displayed status', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [
        {
          id: 'a1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          startAt: '2026-08-01T10:00:00Z',
          endAt: '2026-08-01T10:30:00Z',
          status: 'SCHEDULED',
          cancellationReason: null
        }
      ]
    );
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.link-button'));
    const confirmButton = buttons.find((button) => button.textContent?.trim() === 'Confirmar');
    confirmButton?.click();

    const request = httpMock.expectOne('/api/v1/appointments/a1/confirm');
    request.flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: '2026-08-01T10:00:00Z',
      endAt: '2026-08-01T10:30:00Z',
      status: 'CONFIRMED',
      cancellationReason: null
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Confirmado');
    httpMock.verify();
  });

  it('registers arrival, starts and completes the service', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [
        {
          id: 'a1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          startAt: '2026-08-01T10:00:00Z',
          endAt: '2026-08-01T10:30:00Z',
          status: 'SCHEDULED',
          cancellationReason: null
        }
      ]
    );
    fixture.detectChanges();

    const findButton = (label: string) =>
      Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.link-button')).find(
        (button) => button.textContent?.trim() === label
      );

    findButton('Registrar chegada')?.click();
    httpMock.expectOne('/api/v1/appointments/a1/arrive').flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: '2026-08-01T10:00:00Z',
      endAt: '2026-08-01T10:30:00Z',
      status: 'ARRIVED',
      cancellationReason: null
    });
    fixture.detectChanges();

    findButton('Iniciar atendimento')?.click();
    httpMock.expectOne('/api/v1/appointments/a1/start').flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: '2026-08-01T10:00:00Z',
      endAt: '2026-08-01T10:30:00Z',
      status: 'IN_PROGRESS',
      cancellationReason: null
    });
    fixture.detectChanges();

    findButton('Concluir')?.click();
    httpMock.expectOne('/api/v1/appointments/a1/complete').flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: '2026-08-01T10:00:00Z',
      endAt: '2026-08-01T10:30:00Z',
      status: 'DONE',
      cancellationReason: null
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Realizado');
    httpMock.verify();
  });

  it('marks a no-show', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [
        {
          id: 'a1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          startAt: '2026-08-01T10:00:00Z',
          endAt: '2026-08-01T10:30:00Z',
          status: 'SCHEDULED',
          cancellationReason: null
        }
      ]
    );
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.link-button'));
    const noShowButton = buttons.find((button) => button.textContent?.trim() === 'Não compareceu');
    noShowButton?.click();

    httpMock.expectOne('/api/v1/appointments/a1/no-show').flush({
      id: 'a1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      startAt: '2026-08-01T10:00:00Z',
      endAt: '2026-08-01T10:30:00Z',
      status: 'NO_SHOW',
      cancellationReason: null
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Não compareceu');
    httpMock.verify();
  });
});

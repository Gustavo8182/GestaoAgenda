import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WaitlistPageComponent } from './waitlist-page.component';

async function createComponent(
  clients: unknown[],
  services: unknown[],
  entries: unknown[] = []
): Promise<{ fixture: ComponentFixture<WaitlistPageComponent>; httpMock: HttpTestingController }> {
  await TestBed.configureTestingModule({
    imports: [WaitlistPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(WaitlistPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/clients').flush(clients);
  httpMock.expectOne('/api/v1/catalog/services').flush(services);
  httpMock.expectOne('/api/v1/waitlist').flush(entries);

  return { fixture, httpMock };
}

describe('WaitlistPageComponent', () => {
  it('shows a hint to register clients and services first when there are none', async () => {
    const { fixture, httpMock } = await createComponent([], []);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Cadastre clientes e serviços primeiro');
    httpMock.verify();
  });

  it('shows the empty state when there are clients and services but no waitlist entries', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }]
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum registro na lista de espera');
    httpMock.verify();
  });

  it('creates a waitlist entry with the fields filled in the form', async () => {
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

    const startDate: HTMLInputElement = fixture.nativeElement.querySelector('#preferredStartDate');
    startDate.value = '2026-08-01';
    startDate.dispatchEvent(new Event('input'));

    const endDate: HTMLInputElement = fixture.nativeElement.querySelector('#preferredEndDate');
    endDate.value = '2026-08-15';
    endDate.dispatchEvent(new Event('input'));

    const expiresAt: HTMLInputElement = fixture.nativeElement.querySelector('#expiresAt');
    expiresAt.value = '2026-09-01T00:00';
    expiresAt.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/waitlist');
    expect(request.request.body.clientId).toBe('c1');
    expect(request.request.body.serviceId).toBe('s1');
    expect(request.request.body.preferredStartTime).toBe('09:00:00');
    expect(request.request.body.priority).toBe('NORMAL');

    request.flush({
      id: 'w1',
      serviceId: 's1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      preferredStartDate: '2026-08-01',
      preferredEndDate: '2026-08-15',
      preferredStartTime: '09:00:00',
      preferredEndTime: '18:00:00',
      priority: 'NORMAL',
      expiresAt: '2026-09-01T00:00:00Z',
      status: 'WAITING',
      appointmentId: null
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fulana de Tal');
    expect(fixture.nativeElement.textContent).toContain('Aguardando');
    httpMock.verify();
  });

  it('cancels a waitlist entry', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [
        {
          id: 'w1',
          serviceId: 's1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          preferredStartDate: '2026-08-01',
          preferredEndDate: '2026-08-15',
          preferredStartTime: '09:00:00',
          preferredEndTime: '18:00:00',
          priority: 'NORMAL',
          expiresAt: '2026-09-01T00:00:00Z',
          status: 'WAITING',
          appointmentId: null
        }
      ]
    );
    fixture.detectChanges();

    const cancelButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button--danger');
    cancelButton.click();

    const request = httpMock.expectOne('/api/v1/waitlist/w1/cancel');
    request.flush({
      id: 'w1',
      serviceId: 's1',
      clientName: 'Fulana de Tal',
      serviceName: 'Corte',
      preferredStartDate: '2026-08-01',
      preferredEndDate: '2026-08-15',
      preferredStartTime: '09:00:00',
      preferredEndTime: '18:00:00',
      priority: 'NORMAL',
      expiresAt: '2026-09-01T00:00:00Z',
      status: 'CANCELLED',
      appointmentId: null
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Cancelado');
    httpMock.verify();
  });

  it('converts a waitlist entry into an appointment computing the end time from the service duration', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }],
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [
        {
          id: 'w1',
          serviceId: 's1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          preferredStartDate: '2026-08-01',
          preferredEndDate: '2026-08-15',
          preferredStartTime: '09:00:00',
          preferredEndTime: '18:00:00',
          priority: 'NORMAL',
          expiresAt: '2026-09-01T00:00:00Z',
          status: 'WAITING',
          appointmentId: null
        }
      ]
    );
    fixture.detectChanges();

    const convertButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button');
    convertButton.click();
    fixture.detectChanges();

    const startInput: HTMLInputElement = fixture.nativeElement.querySelector('input[formcontrolname="startAt"]');
    startInput.value = '2026-08-05T10:00';
    startInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.row-form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/waitlist/w1/convert');
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
      cancellationReason: null,
      seriesId: null
    });

    httpMock.expectOne('/api/v1/clients').flush([{ id: 'c1', name: 'Fulana de Tal', phone: '21999999999' }]);
    httpMock
      .expectOne('/api/v1/catalog/services')
      .flush([{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }]);
    httpMock.expectOne('/api/v1/waitlist').flush([
      {
        id: 'w1',
        serviceId: 's1',
        clientName: 'Fulana de Tal',
        serviceName: 'Corte',
        preferredStartDate: '2026-08-01',
        preferredEndDate: '2026-08-15',
        preferredStartTime: '09:00:00',
        preferredEndTime: '18:00:00',
        priority: 'NORMAL',
        expiresAt: '2026-09-01T00:00:00Z',
        status: 'CONVERTED',
        appointmentId: 'a1'
      }
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Convertido em agendamento');
    httpMock.verify();
  });
});

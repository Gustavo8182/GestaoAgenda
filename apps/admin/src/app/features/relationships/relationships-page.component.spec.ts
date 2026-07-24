import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RelationshipsPageComponent } from './relationships-page.component';

async function createComponent(
  services: unknown[],
  contacts: unknown[] = [],
  assignableMembers: unknown[] = [{ userId: 'u1', displayName: 'Usuária de teste' }]
): Promise<{ fixture: ComponentFixture<RelationshipsPageComponent>; httpMock: HttpTestingController }> {
  await TestBed.configureTestingModule({
    imports: [RelationshipsPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(RelationshipsPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/catalog/services').flush(services);
  httpMock.expectOne('/api/v1/relationships/assignable-members').flush(assignableMembers);
  httpMock.expectOne('/api/v1/relationships').flush(contacts);

  return { fixture, httpMock };
}

function contact(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: 'r1',
    name: 'Fulana de Tal',
    phone: '21999999999',
    origin: 'Instagram',
    status: 'NEW_CONTACT',
    lastInteractionAt: '2026-08-01T12:00:00Z',
    nextAction: null,
    nextActionAt: null,
    responsibleUserId: 'u1',
    responsibleName: 'Usuária de teste',
    clientId: null,
    appointmentId: null,
    pendingContact: false,
    ...overrides
  };
}

describe('RelationshipsPageComponent', () => {
  it('shows the empty state when there are no contacts', async () => {
    const { fixture, httpMock } = await createComponent([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum contato cadastrado');
    httpMock.verify();
  });

  it('creates a contact with the fields filled in the form', async () => {
    const { fixture, httpMock } = await createComponent([]);
    fixture.detectChanges();

    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector('#name');
    nameInput.value = 'Fulana de Tal';
    nameInput.dispatchEvent(new Event('input'));

    const phoneInput: HTMLInputElement = fixture.nativeElement.querySelector('#phone');
    phoneInput.value = '21999999999';
    phoneInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/relationships');
    expect(request.request.body.name).toBe('Fulana de Tal');
    expect(request.request.body.phone).toBe('21999999999');

    request.flush(contact());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fulana de Tal');
    expect(fixture.nativeElement.textContent).toContain('Novo contato');
    httpMock.verify();
  });

  it('shows the pending contact indicator and badge', async () => {
    const { fixture, httpMock } = await createComponent([], [contact({ pendingContact: true })]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Contato pendente');
    const indicators: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.indicator strong'));
    expect(indicators[0].textContent?.trim()).toBe('1');
    httpMock.verify();
  });

  it('updates status and next action', async () => {
    const { fixture, httpMock } = await createComponent([], [contact()]);
    fixture.detectChanges();

    const updateButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button');
    updateButton.click();
    fixture.detectChanges();

    const statusSelect: HTMLSelectElement = fixture.nativeElement.querySelector('select[formcontrolname="status"]');
    statusSelect.value = 'AWAITING_RESPONSE';
    statusSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.row-form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/relationships/r1/update');
    expect(request.request.body.status).toBe('AWAITING_RESPONSE');

    request.flush(contact({ status: 'AWAITING_RESPONSE' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Aguardando resposta');
    httpMock.verify();
  });

  it('reassigns the responsible member and shows the updated name', async () => {
    const { fixture, httpMock } = await createComponent([], [contact()], [
      { userId: 'u1', displayName: 'Usuária de teste' },
      { userId: 'u2', displayName: 'Secretária Nova' }
    ]);
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.link-button'));
    const reassignButton = buttons.find((button) => button.textContent?.trim() === 'Reatribuir');
    reassignButton?.click();
    fixture.detectChanges();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector(
      'select[formcontrolname="responsibleUserId"]'
    );
    expect(select.value).toBe('u1');
    select.value = 'u2';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.row-form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/relationships/r1/reassign');
    expect(request.request.body).toEqual({ responsibleUserId: 'u2' });

    request.flush(contact({ responsibleUserId: 'u2', responsibleName: 'Secretária Nova' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Secretária Nova');
    httpMock.verify();
  });

  it('converts a contact into a client and appointment computing the end time from the service duration', async () => {
    const { fixture, httpMock } = await createComponent(
      [{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }],
      [contact()]
    );
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.link-button'));
    const convertButton = buttons.find((button) => button.textContent?.includes('Converter'));
    convertButton?.click();
    fixture.detectChanges();

    const serviceSelect: HTMLSelectElement = fixture.nativeElement.querySelector('select[formcontrolname="serviceId"]');
    serviceSelect.value = serviceSelect.options[1].value;
    serviceSelect.dispatchEvent(new Event('change'));

    const startInput: HTMLInputElement = fixture.nativeElement.querySelector('input[formcontrolname="startAt"]');
    startInput.value = '2026-08-05T10:00';
    startInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.row-form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/relationships/r1/convert');
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

    httpMock
      .expectOne('/api/v1/catalog/services')
      .flush([{ id: 's1', name: 'Corte', durationMinutes: 30, color: null, displayOrder: 0, requiresConfirmation: false, active: true }]);
    httpMock.expectOne('/api/v1/relationships/assignable-members').flush([{ userId: 'u1', displayName: 'Usuária de teste' }]);
    httpMock.expectOne('/api/v1/relationships').flush([
      contact({ status: 'SCHEDULED', clientId: 'c1', appointmentId: 'a1' })
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Agendado');
    httpMock.verify();
  });
});

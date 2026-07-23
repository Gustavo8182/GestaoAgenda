import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ClientsPageComponent } from './clients-page.component';

function setInputValue(fixture: ComponentFixture<ClientsPageComponent>, selector: string, value: string): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

async function createComponent(): Promise<{
  fixture: ComponentFixture<ClientsPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [ClientsPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ClientsPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/clients').flush([]);

  return { fixture, httpMock };
}

describe('ClientsPageComponent', () => {
  it('shows the empty state when there are no clients', async () => {
    const { fixture, httpMock } = await createComponent();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhuma cliente encontrada');
    httpMock.verify();
  });

  it('does not submit when the form is invalid', async () => {
    const { fixture, httpMock } = await createComponent();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectNone('/api/v1/clients');
    httpMock.verify();
  });

  it('creates a client and shows it in the list without a duplicate warning', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Fulana de Tal');
    setInputValue(fixture, '#phone', '(21) 99999-9999');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/clients');
    request.flush({
      client: { id: '1', name: 'Fulana de Tal', phone: '(21) 99999-9999', alternatePhone: null, origin: null, notes: null, contactRestricted: false, contactRestrictionReason: null },
      possibleDuplicate: false
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fulana de Tal');
    expect(fixture.nativeElement.textContent).not.toContain('Já existe uma cliente cadastrada');
    httpMock.verify();
  });

  it('shows a duplicate warning when the API flags one', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Fulana da Silva');
    setInputValue(fixture, '#phone', '+55 21 99999-9999');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/clients');
    request.flush({
      client: { id: '2', name: 'Fulana da Silva', phone: '+55 21 99999-9999', alternatePhone: null, origin: null, notes: null, contactRestricted: false, contactRestrictionReason: null },
      possibleDuplicate: true
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Já existe uma cliente cadastrada');
    httpMock.verify();
  });

  it('creates a client with alternate phone, origin and notes and shows them in the list', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Fulana de Tal');
    setInputValue(fixture, '#phone', '(21) 99999-9999');
    setInputValue(fixture, '#alternatePhone', '(21) 98888-7777');
    setInputValue(fixture, '#origin', 'Indicação de amiga');
    setInputValue(fixture, '#notes', 'Prefere manhãs');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/clients');
    expect(request.request.body.alternatePhone).toBe('(21) 98888-7777');
    expect(request.request.body.origin).toBe('Indicação de amiga');
    expect(request.request.body.notes).toBe('Prefere manhãs');

    request.flush({
      client: {
        id: '1',
        name: 'Fulana de Tal',
        phone: '(21) 99999-9999',
        alternatePhone: '(21) 98888-7777',
        origin: 'Indicação de amiga',
        notes: 'Prefere manhãs',
        contactRestricted: false,
        contactRestrictionReason: null
      },
      possibleDuplicate: false
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Alt.: (21) 98888-7777');
    expect(fixture.nativeElement.textContent).toContain('Indicação de amiga');
    expect(fixture.nativeElement.textContent).toContain('Prefere manhãs');
    httpMock.verify();
  });

  it('shows and hides the appointment history for a client', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Fulana de Tal');
    setInputValue(fixture, '#phone', '(21) 99999-9999');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/clients').flush({
      client: { id: '1', name: 'Fulana de Tal', phone: '(21) 99999-9999', alternatePhone: null, origin: null, notes: null, contactRestricted: false, contactRestrictionReason: null },
      possibleDuplicate: false
    });
    fixture.detectChanges();

    const historyButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button');
    historyButton.click();
    fixture.detectChanges();

    const request = httpMock.expectOne((req) => req.url === '/api/v1/appointments' && req.params.get('clientId') === '1');
    request.flush([
      {
        id: 'a1',
        clientName: 'Fulana de Tal',
        serviceName: 'Corte',
        startAt: '2026-08-01T10:00:00Z',
        endAt: '2026-08-01T10:30:00Z',
        status: 'DONE',
        cancellationReason: null
      }
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Realizado');
    expect(historyButton.textContent?.trim()).toBe('Ocultar histórico');

    historyButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Realizado');
    expect(historyButton.textContent?.trim()).toBe('Ver histórico');
    httpMock.verify();
  });

  it('restricts contact for a client and shows the reason, then lifts the restriction', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Fulana de Tal');
    setInputValue(fixture, '#phone', '(21) 99999-9999');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    httpMock.expectOne('/api/v1/clients').flush({
      client: {
        id: '1',
        name: 'Fulana de Tal',
        phone: '(21) 99999-9999',
        alternatePhone: null,
        origin: null,
        notes: null,
        contactRestricted: false,
        contactRestrictionReason: null
      },
      possibleDuplicate: false
    });
    fixture.detectChanges();

    const restrictButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button--danger');
    restrictButton.click();
    fixture.detectChanges();

    setInputValue(fixture, '#restrict-reason-1', 'Pediu para não ser mais contatada.');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.row-form').dispatchEvent(new Event('submit'));

    const restrictRequest = httpMock.expectOne('/api/v1/clients/1/restrict-contact');
    expect(restrictRequest.request.body.reason).toBe('Pediu para não ser mais contatada.');
    restrictRequest.flush({
      id: '1',
      name: 'Fulana de Tal',
      phone: '(21) 99999-9999',
      alternatePhone: null,
      origin: null,
      notes: null,
      contactRestricted: true,
      contactRestrictionReason: 'Pediu para não ser mais contatada.'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Não contatar');
    expect(fixture.nativeElement.textContent).toContain('Pediu para não ser mais contatada.');

    const actionButtons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.client-actions .link-button')
    );
    const liftButton = actionButtons.find((button) => button.textContent?.trim() === 'Remover restrição')!;
    liftButton.click();

    const liftRequest = httpMock.expectOne('/api/v1/clients/1/lift-contact-restriction');
    expect(liftRequest.request.method).toBe('POST');
    liftRequest.flush({
      id: '1',
      name: 'Fulana de Tal',
      phone: '(21) 99999-9999',
      alternatePhone: null,
      origin: null,
      notes: null,
      contactRestricted: false,
      contactRestrictionReason: null
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Não contatar');
    httpMock.verify();
  });

  it('searches clients by the query typed after a debounce', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#search', 'beltra');
    await new Promise((resolve) => setTimeout(resolve, 350));

    const request = httpMock.expectOne((req) => req.url === '/api/v1/clients' && req.params.get('query') === 'beltra');
    request.flush([
      { id: '3', name: 'Beltrana Souza', phone: '21977775678', alternatePhone: null, origin: null, notes: null, contactRestricted: false, contactRestrictionReason: null }
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Beltrana Souza');
    httpMock.verify();
  });
});

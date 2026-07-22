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

    expect(fixture.nativeElement.textContent).toContain('Nenhuma cliente cadastrada');
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
      client: { id: '1', name: 'Fulana de Tal', phone: '(21) 99999-9999' },
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
      client: { id: '2', name: 'Fulana da Silva', phone: '+55 21 99999-9999' },
      possibleDuplicate: true
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Já existe uma cliente cadastrada');
    httpMock.verify();
  });
});

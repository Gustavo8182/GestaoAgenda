import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ServicesPageComponent } from './services-page.component';

function setInputValue(fixture: ComponentFixture<ServicesPageComponent>, selector: string, value: string): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

async function createComponent(): Promise<{
  fixture: ComponentFixture<ServicesPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [ServicesPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ServicesPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/catalog/services').flush([]);

  return { fixture, httpMock };
}

describe('ServicesPageComponent', () => {
  it('lists services returned by the API', async () => {
    const { fixture, httpMock } = await createComponent();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum serviço cadastrado');
    httpMock.verify();
  });

  it('does not submit when the form is invalid', async () => {
    const { fixture, httpMock } = await createComponent();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectNone('/api/v1/catalog/services');
    httpMock.verify();
  });

  it('creates a service and shows it in the list', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Limpeza de pele');
    setInputValue(fixture, '#durationMinutes', '60');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/catalog/services');
    expect(request.request.method).toBe('POST');
    request.flush({ id: '1', name: 'Limpeza de pele', durationMinutes: 60 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Limpeza de pele');
    expect(fixture.nativeElement.textContent).toContain('60 min');
    httpMock.verify();
  });
});

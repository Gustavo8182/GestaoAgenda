import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { ServicesPageComponent } from './services-page.component';

function setInputValue(fixture: ComponentFixture<ServicesPageComponent>, selector: string, value: string): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function setChecked(fixture: ComponentFixture<ServicesPageComponent>, selector: string, checked: boolean): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
  input.checked = checked;
  input.dispatchEvent(new Event('change'));
}

async function createComponent(): Promise<{
  fixture: ComponentFixture<ServicesPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [ServicesPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { isOwner: () => true } }
    ]
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

  it('creates a service with color and confirmation and shows it in the list', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Limpeza de pele');
    setInputValue(fixture, '#durationMinutes', '60');
    setChecked(fixture, '#requiresConfirmation', true);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/catalog/services');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.requiresConfirmation).toBe(true);
    request.flush({
      id: '1',
      name: 'Limpeza de pele',
      durationMinutes: 60,
      color: '#94a3b8',
      displayOrder: 0,
      requiresConfirmation: true,
      active: true,
      bufferMinutes: 0
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Limpeza de pele');
    expect(fixture.nativeElement.textContent).toContain('60 min');
    expect(fixture.nativeElement.textContent).toContain('Exige confirmação');
    httpMock.verify();
  });

  it('creates a service with a buffer and shows it in the list', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Limpeza de pele');
    setInputValue(fixture, '#durationMinutes', '60');
    setInputValue(fixture, '#bufferMinutes', '15');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/catalog/services');
    expect(request.request.body.bufferMinutes).toBe(15);
    request.flush({
      id: '3',
      name: 'Limpeza de pele',
      durationMinutes: 60,
      color: '#94a3b8',
      displayOrder: 0,
      requiresConfirmation: false,
      active: true,
      bufferMinutes: 15
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Intervalo: 15 min');
    httpMock.verify();
  });

  it('deactivates a service and keeps it in the list marked as inactive', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Corte');
    setInputValue(fixture, '#durationMinutes', '30');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const createRequest = httpMock.expectOne('/api/v1/catalog/services');
    createRequest.flush({
      id: '2',
      name: 'Corte',
      durationMinutes: 30,
      color: null,
      displayOrder: 0,
      requiresConfirmation: false,
      active: true,
      bufferMinutes: 0
    });
    fixture.detectChanges();

    const deactivateButton: HTMLButtonElement = fixture.nativeElement.querySelector('.deactivate-button');
    deactivateButton.click();

    const deactivateRequest = httpMock.expectOne('/api/v1/catalog/services/2/deactivate');
    expect(deactivateRequest.request.method).toBe('POST');
    deactivateRequest.flush({
      id: '2',
      name: 'Corte',
      durationMinutes: 30,
      color: null,
      displayOrder: 0,
      requiresConfirmation: false,
      active: false,
      bufferMinutes: 0
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Corte');
    expect(fixture.nativeElement.textContent).toContain('Inativo');
    expect(fixture.nativeElement.querySelector('.deactivate-button')).toBeNull();

    const reactivateButton: HTMLButtonElement = fixture.nativeElement.querySelector('.reactivate-button');
    reactivateButton.click();

    const reactivateRequest = httpMock.expectOne('/api/v1/catalog/services/2/reactivate');
    expect(reactivateRequest.request.method).toBe('POST');
    reactivateRequest.flush({
      id: '2',
      name: 'Corte',
      durationMinutes: 30,
      color: null,
      displayOrder: 0,
      requiresConfirmation: false,
      active: true,
      bufferMinutes: 0
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Inativo');
    expect(fixture.nativeElement.querySelector('.reactivate-button')).toBeNull();
    expect(fixture.nativeElement.querySelector('.deactivate-button')).not.toBeNull();
    httpMock.verify();
  });

  it('edits a service pre-filling the current values and shows the updated fields', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#name', 'Corte');
    setInputValue(fixture, '#durationMinutes', '30');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const createRequest = httpMock.expectOne('/api/v1/catalog/services');
    createRequest.flush({
      id: '2',
      name: 'Corte',
      durationMinutes: 30,
      color: '#94a3b8',
      displayOrder: 0,
      requiresConfirmation: false,
      active: true,
      bufferMinutes: 0
    });
    fixture.detectChanges();

    const editButton: HTMLButtonElement = fixture.nativeElement.querySelector('.edit-button');
    editButton.click();
    fixture.detectChanges();

    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector('.edit-form input[formcontrolname="name"]');
    expect(nameInput.value).toBe('Corte');
    const durationInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '.edit-form input[formcontrolname="durationMinutes"]'
    );
    expect(durationInput.value).toBe('30');

    nameInput.value = 'Corte premium';
    nameInput.dispatchEvent(new Event('input'));
    durationInput.value = '45';
    durationInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.edit-form').dispatchEvent(new Event('submit'));

    const editRequest = httpMock.expectOne('/api/v1/catalog/services/2/edit');
    expect(editRequest.request.body.name).toBe('Corte premium');
    expect(editRequest.request.body.durationMinutes).toBe(45);

    editRequest.flush({
      id: '2',
      name: 'Corte premium',
      durationMinutes: 45,
      color: '#94a3b8',
      displayOrder: 0,
      requiresConfirmation: false,
      active: true,
      bufferMinutes: 0
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Corte premium');
    expect(fixture.nativeElement.textContent).toContain('45 min');
    expect(fixture.nativeElement.querySelector('.edit-form')).toBeNull();
    httpMock.verify();
  });
});

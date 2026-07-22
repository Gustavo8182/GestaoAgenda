import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LoginPageComponent } from './login-page.component';

function setInputValue(fixture: ComponentFixture<LoginPageComponent>, selector: string, value: string): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

async function createComponent(): Promise<{
  fixture: ComponentFixture<LoginPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [LoginPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(LoginPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/auth/me').flush('unauthorized', { status: 401, statusText: 'Unauthorized' });

  return { fixture, httpMock };
}

describe('LoginPageComponent', () => {
  it('does not submit the login request when the form is invalid', async () => {
    const { fixture, httpMock } = await createComponent();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectNone('/api/v1/auth/login');
    httpMock.verify();
  });

  it('submits valid credentials to the login endpoint', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#email', 'dona@exemplo.test');
    setInputValue(fixture, '#password', 'TrocarSenha123!');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/auth/login');
    request.flush({
      user: { id: '1', email: 'dona@exemplo.test', displayName: 'Dona' },
      organization: { organizationId: '2', organizationName: 'Clínica de teste', role: 'OWNER' }
    });

    httpMock.verify();
  });

  it('shows a generic error message when the credentials are rejected', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#email', 'dona@exemplo.test');
    setInputValue(fixture, '#password', 'senha-errada');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/auth/login');
    request.flush({ code: 'invalid_credentials' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('E-mail ou senha inválidos.');
    httpMock.verify();
  });
});

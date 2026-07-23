import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmPasswordResetPageComponent } from './confirm-password-reset-page.component';

async function createComponent(token: string | null): Promise<{
  fixture: ComponentFixture<ConfirmPasswordResetPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [ConfirmPasswordResetPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) }
        }
      }
    ]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(ConfirmPasswordResetPageComponent);
  fixture.detectChanges();

  return { fixture, httpMock };
}

describe('ConfirmPasswordResetPageComponent', () => {
  it('shows an error and no form when the token is missing from the URL', async () => {
    const { fixture, httpMock } = await createComponent(null);

    expect(fixture.nativeElement.textContent).toContain('Link inválido');
    expect(fixture.nativeElement.querySelector('#newPassword')).toBeNull();
    httpMock.verify();
  });

  it('does not submit when the new password is too short', async () => {
    const { fixture, httpMock } = await createComponent('abc123token');

    const input: HTMLInputElement = fixture.nativeElement.querySelector('#newPassword');
    input.value = 'curta';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    httpMock.expectNone('/api/v1/auth/password-reset/confirm');
    httpMock.verify();
  });

  it('submits the token and new password, then shows a success message', async () => {
    const { fixture, httpMock } = await createComponent('abc123token');

    const input: HTMLInputElement = fixture.nativeElement.querySelector('#newPassword');
    input.value = 'NovaSenhaForte456!';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/auth/password-reset/confirm');
    expect(request.request.body).toEqual({ token: 'abc123token', newPassword: 'NovaSenhaForte456!' });
    request.flush({ message: 'Senha redefinida com sucesso.' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Senha redefinida com sucesso');
    httpMock.verify();
  });

  it('shows the server error message when the token is invalid', async () => {
    const { fixture, httpMock } = await createComponent('abc123token');

    const input: HTMLInputElement = fixture.nativeElement.querySelector('#newPassword');
    input.value = 'NovaSenhaForte456!';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/auth/password-reset/confirm');
    request.flush(
      { code: 'invalid_password_reset_token', message: 'Link de redefinição inválido ou expirado.' },
      { status: 400, statusText: 'Bad Request' }
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Link de redefinição inválido ou expirado.');
    httpMock.verify();
  });
});

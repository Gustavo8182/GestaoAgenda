import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RequestPasswordResetPageComponent } from './request-password-reset-page.component';

async function createComponent(): Promise<{
  fixture: ComponentFixture<RequestPasswordResetPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [RequestPasswordResetPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(RequestPasswordResetPageComponent);
  fixture.detectChanges();

  return { fixture, httpMock };
}

describe('RequestPasswordResetPageComponent', () => {
  it('does not submit when the email is invalid', async () => {
    const { fixture, httpMock } = await createComponent();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    httpMock.expectNone('/api/v1/auth/password-reset/request');
    httpMock.verify();
  });

  it('submits the email and shows the generic result message', async () => {
    const { fixture, httpMock } = await createComponent();

    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector('#email');
    emailInput.value = 'dona@exemplo.test';
    emailInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/auth/password-reset/request');
    expect(request.request.body.email).toBe('dona@exemplo.test');
    request.flush({
      message: 'Se este e-mail estiver cadastrado, você vai receber instruções para redefinir a senha.'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Se este e-mail estiver cadastrado');
    httpMock.verify();
  });
});

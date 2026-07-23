import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { MembersPageComponent } from './members-page.component';

function setInputValue(fixture: ComponentFixture<MembersPageComponent>, selector: string, value: string): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

async function createComponent(): Promise<{
  fixture: ComponentFixture<MembersPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [MembersPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { isOwner: () => true } }
    ]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(MembersPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/organizations/members').flush([]);

  return { fixture, httpMock };
}

describe('MembersPageComponent', () => {
  it('lists members returned by the API', async () => {
    const { fixture, httpMock } = await createComponent();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhuma usuária cadastrada');
    httpMock.verify();
  });

  it('does not submit when the form is invalid', async () => {
    const { fixture, httpMock } = await createComponent();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectNone('/api/v1/organizations/members');
    httpMock.verify();
  });

  it('invites a member and shows it in the list', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#displayName', 'Secretária Nova');
    setInputValue(fixture, '#email', 'secretaria@exemplo.test');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/organizations/members');
    expect(request.request.method).toBe('POST');
    request.flush({
      id: '1',
      email: 'secretaria@exemplo.test',
      displayName: 'Secretária Nova',
      role: 'SECRETARY',
      status: 'INVITED'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Secretária Nova');
    expect(fixture.nativeElement.textContent).toContain('Convite pendente');
    httpMock.verify();
  });

  it('shows a specific error message when the email is already registered', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#displayName', 'Secretária Nova');
    setInputValue(fixture, '#email', 'dona@exemplo.test');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/organizations/members');
    request.flush(
      { code: 'email_already_registered', message: 'Já existe uma usuária cadastrada com este e-mail.' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Já existe uma usuária cadastrada com este e-mail.');
    httpMock.verify();
  });

  it('disables an active member and reactivates it later', async () => {
    const { fixture, httpMock } = await createComponent();

    setInputValue(fixture, '#displayName', 'Secretária Nova');
    setInputValue(fixture, '#email', 'secretaria@exemplo.test');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    httpMock.expectOne('/api/v1/organizations/members').flush({
      id: '1',
      email: 'secretaria@exemplo.test',
      displayName: 'Secretária Nova',
      role: 'SECRETARY',
      status: 'ACTIVE'
    });
    fixture.detectChanges();

    const disableButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button--danger');
    disableButton.click();

    const disableRequest = httpMock.expectOne('/api/v1/organizations/members/1/disable');
    expect(disableRequest.request.method).toBe('POST');
    disableRequest.flush({
      id: '1',
      email: 'secretaria@exemplo.test',
      displayName: 'Secretária Nova',
      role: 'SECRETARY',
      status: 'DISABLED'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Desativada');

    const reactivateButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button');
    reactivateButton.click();

    const reactivateRequest = httpMock.expectOne('/api/v1/organizations/members/1/reactivate');
    expect(reactivateRequest.request.method).toBe('POST');
    reactivateRequest.flush({
      id: '1',
      email: 'secretaria@exemplo.test',
      displayName: 'Secretária Nova',
      role: 'SECRETARY',
      status: 'ACTIVE'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ativa');
    httpMock.verify();
  });
});

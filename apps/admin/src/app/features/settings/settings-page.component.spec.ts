import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { SettingsPageComponent } from './settings-page.component';

async function createComponent(
  businessHours: unknown[] = [],
  blocks: unknown[] = []
): Promise<{ fixture: ComponentFixture<SettingsPageComponent>; httpMock: HttpTestingController }> {
  await TestBed.configureTestingModule({
    imports: [SettingsPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { isOwner: () => true } }
    ]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(SettingsPageComponent);
  fixture.detectChanges();

  httpMock.expectOne('/api/v1/availability/business-hours').flush(businessHours);
  httpMock.expectOne('/api/v1/availability/blocks').flush(blocks);
  fixture.detectChanges();

  return { fixture, httpMock };
}

describe('SettingsPageComponent', () => {
  it('shows all days unchecked when no business hours are configured', async () => {
    const { fixture, httpMock } = await createComponent();

    const checkboxes: HTMLInputElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('input[type="checkbox"]')
    );
    expect(checkboxes.length).toBe(7);
    expect(checkboxes.every((checkbox) => !checkbox.checked)).toBe(true);
    httpMock.verify();
  });

  it('pre-fills configured days as checked with their times', async () => {
    const { fixture, httpMock } = await createComponent([
      { dayOfWeek: 'MONDAY', startTime: '09:00:00', endTime: '18:00:00' }
    ]);

    expect(fixture.nativeElement.textContent).toContain('Segunda-feira');
    const timeInputs: HTMLInputElement[] = Array.from(fixture.nativeElement.querySelectorAll('input[type="time"]'));
    expect(timeInputs[0].value).toBe('09:00');
    expect(timeInputs[1].value).toBe('18:00');
    httpMock.verify();
  });

  it('saves only the enabled days when the form is submitted', async () => {
    const { fixture, httpMock } = await createComponent();

    const mondayCheckbox: HTMLInputElement = fixture.nativeElement.querySelector('input[type="checkbox"]');
    mondayCheckbox.checked = true;
    mondayCheckbox.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const saveButton: HTMLButtonElement = fixture.nativeElement.querySelector('.card button');
    saveButton.click();

    const request = httpMock.expectOne('/api/v1/availability/business-hours');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual([{ dayOfWeek: 'MONDAY', startTime: '09:00:00', endTime: '18:00:00' }]);
    request.flush(request.request.body);

    httpMock.verify();
  });

  it('creates a block and lists it', async () => {
    const { fixture, httpMock } = await createComponent();

    const startInput: HTMLInputElement = fixture.nativeElement.querySelector('#blockStartAt');
    startInput.value = '2026-08-01T12:00';
    startInput.dispatchEvent(new Event('input'));

    const endInput: HTMLInputElement = fixture.nativeElement.querySelector('#blockEndAt');
    endInput.value = '2026-08-01T13:00';
    endInput.dispatchEvent(new Event('input'));

    const reasonInput: HTMLInputElement = fixture.nativeElement.querySelector('#blockReason');
    reasonInput.value = 'Almoço';
    reasonInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.block-form').dispatchEvent(new Event('submit'));

    const request = httpMock.expectOne('/api/v1/availability/blocks');
    expect(request.request.body.reason).toBe('Almoço');
    request.flush({
      id: 'b1',
      startAt: request.request.body.startAt,
      endAt: request.request.body.endAt,
      reason: 'Almoço'
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Almoço');
    httpMock.verify();
  });

  it('removes a block from the list', async () => {
    const { fixture, httpMock } = await createComponent([], [
      { id: 'b1', startAt: '2026-08-01T12:00:00Z', endAt: '2026-08-01T13:00:00Z', reason: 'Almoço' }
    ]);

    const removeButton: HTMLButtonElement = fixture.nativeElement.querySelector('.link-button--danger');
    removeButton.click();

    httpMock.expectOne('/api/v1/availability/blocks/b1').flush(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum bloqueio cadastrado');
    httpMock.verify();
  });
});

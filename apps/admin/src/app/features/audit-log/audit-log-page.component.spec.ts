import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuditLogPageComponent } from './audit-log-page.component';

async function createComponent(): Promise<{
  fixture: ComponentFixture<AuditLogPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [AuditLogPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AuditLogPageComponent);
  fixture.detectChanges();

  return { fixture, httpMock };
}

describe('AuditLogPageComponent', () => {
  it('shows the empty state when there are no audit entries', async () => {
    const { fixture, httpMock } = await createComponent();

    httpMock.expectOne('/api/v1/audit-log').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum registro de auditoria ainda');
    httpMock.verify();
  });

  it('shows entries with a friendly action label, actor and metadata', async () => {
    const { fixture, httpMock } = await createComponent();

    httpMock.expectOne('/api/v1/audit-log').flush([
      {
        id: '1',
        actorName: 'Dona',
        action: 'APPOINTMENT_CANCELLED',
        entityType: 'APPOINTMENT',
        entityId: 'a1',
        metadata: { reason: 'Cliente desmarcou.' },
        occurredAt: '2026-08-01T10:00:00Z'
      }
    ]);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Agendamento cancelado');
    expect(text).toContain('Agendamento');
    expect(text).toContain('Dona');
    expect(text).toContain('reason');
    expect(text).toContain('Cliente desmarcou.');
    httpMock.verify();
  });

  it('falls back to the raw action name when there is no friendly label', async () => {
    const { fixture, httpMock } = await createComponent();

    httpMock.expectOne('/api/v1/audit-log').flush([
      {
        id: '1',
        actorName: 'Dona',
        action: 'SOMETHING_UNMAPPED',
        entityType: null,
        entityId: null,
        metadata: {},
        occurredAt: '2026-08-01T10:00:00Z'
      }
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('SOMETHING_UNMAPPED');
    httpMock.verify();
  });
});

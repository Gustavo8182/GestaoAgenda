import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardPageComponent } from './dashboard-page.component';

async function createComponent(): Promise<{
  fixture: ComponentFixture<DashboardPageComponent>;
  httpMock: HttpTestingController;
}> {
  await TestBed.configureTestingModule({
    imports: [DashboardPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(DashboardPageComponent);
  fixture.detectChanges();

  return { fixture, httpMock };
}

describe('DashboardPageComponent', () => {
  it('shows a message when there is no upcoming appointment', async () => {
    const { fixture, httpMock } = await createComponent();

    httpMock.expectOne('/api/v1/dashboard').flush({
      todayAppointments: [],
      nextAppointment: null,
      todayBlocks: [],
      week: { scheduledCount: 0, completedCount: 0, cancelledCount: 0, noShowCount: 0 }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum atendimento futuro agendado.');
    expect(fixture.nativeElement.textContent).toContain('Nenhum agendamento para hoje.');
    expect(fixture.nativeElement.textContent).toContain('Nenhum bloqueio hoje.');
    httpMock.verify();
  });

  it('shows the next appointment, today list and week summary', async () => {
    const { fixture, httpMock } = await createComponent();

    httpMock.expectOne('/api/v1/dashboard').flush({
      todayAppointments: [
        {
          id: 'a1',
          clientName: 'Fulana de Tal',
          serviceName: 'Corte',
          startAt: '2026-08-05T15:00:00Z',
          endAt: '2026-08-05T15:30:00Z',
          status: 'SCHEDULED',
          cancellationReason: null
        },
        {
          id: 'a2',
          clientName: 'Beltrana',
          serviceName: 'Escova',
          startAt: '2026-08-05T20:00:00Z',
          endAt: '2026-08-05T20:30:00Z',
          status: 'CANCELLED',
          cancellationReason: 'Motivo'
        }
      ],
      nextAppointment: {
        id: 'a1',
        clientName: 'Fulana de Tal',
        serviceName: 'Corte',
        startAt: '2026-08-05T15:00:00Z',
        endAt: '2026-08-05T15:30:00Z',
        status: 'SCHEDULED',
        cancellationReason: null
      },
      todayBlocks: [{ id: 'b1', startAt: '2026-08-05T18:00:00Z', endAt: '2026-08-05T19:00:00Z', reason: 'Almoço' }],
      week: { scheduledCount: 3, completedCount: 2, cancelledCount: 1, noShowCount: 1 }
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Fulana de Tal');
    expect(text).toContain('Beltrana');
    expect(text).toContain('Cancelado');
    expect(text).toContain('Almoço');
    expect(text).toContain('3');
    expect(text).toContain('2');
    expect(text).toContain('1');
    httpMock.verify();
  });
});

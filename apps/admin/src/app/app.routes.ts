import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const appRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard'
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login-page.component').then((m) => m.LoginPageComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent)
  },
  {
    path: 'agenda',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/agenda/agenda-page.component').then((m) => m.AgendaPageComponent)
  },
  {
    path: 'clientes',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/clients/clients-page.component').then((m) => m.ClientsPageComponent)
  },
  {
    path: 'servicos',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/catalog/services-page.component').then((m) => m.ServicesPageComponent)
  },
  {
    path: 'lista-de-espera',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/waitlist/waitlist-page.component').then((m) => m.WaitlistPageComponent)
  },
  {
    path: 'relacionamento',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/relationships/relationships-page.component').then(
        (m) => m.RelationshipsPageComponent
      )
  },
  {
    path: 'configuracoes',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/settings/settings-page.component').then((m) => m.SettingsPageComponent)
  },
  {
    path: 'auditoria',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/audit-log/audit-log-page.component').then((m) => m.AuditLogPageComponent)
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];

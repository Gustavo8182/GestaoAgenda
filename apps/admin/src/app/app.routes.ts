import { Routes } from '@angular/router';

export const appRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard'
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent)
  },
  {
    path: 'agenda',
    loadComponent: () =>
      import('./features/agenda/agenda-page.component').then((m) => m.AgendaPageComponent)
  },
  {
    path: 'clientes',
    loadComponent: () =>
      import('./features/clients/clients-page.component').then((m) => m.ClientsPageComponent)
  },
  {
    path: 'servicos',
    loadComponent: () =>
      import('./features/catalog/services-page.component').then((m) => m.ServicesPageComponent)
  },
  {
    path: 'lista-de-espera',
    loadComponent: () =>
      import('./features/waitlist/waitlist-page.component').then((m) => m.WaitlistPageComponent)
  },
  {
    path: 'relacionamento',
    loadComponent: () =>
      import('./features/relationships/relationships-page.component').then(
        (m) => m.RelationshipsPageComponent
      )
  },
  {
    path: 'configuracoes',
    loadComponent: () =>
      import('./features/settings/settings-page.component').then((m) => m.SettingsPageComponent)
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];

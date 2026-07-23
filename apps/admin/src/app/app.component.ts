import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/auth/auth.service';

interface NavigationItem {
  readonly label: string;
  readonly path: string;
}

const AUTH_ROUTE_PREFIXES = ['/login', '/esqueci-senha', '/redefinir-senha', '/aceitar-convite'];

function isAuthRouteUrl(url: string): boolean {
  return AUTH_ROUTE_PREFIXES.some((prefix) => url.startsWith(prefix));
}

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {
  private readonly router = inject(Router);
  protected readonly authService = inject(AuthService);

  private static readonly BASE_NAVIGATION: readonly NavigationItem[] = [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Agenda', path: '/agenda' },
    { label: 'Clientes', path: '/clientes' },
    { label: 'Serviços', path: '/servicos' },
    { label: 'Lista de espera', path: '/lista-de-espera' },
    { label: 'Relacionamento', path: '/relacionamento' },
    { label: 'Configurações', path: '/configuracoes' },
    { label: 'Auditoria', path: '/auditoria' }
  ];

  protected readonly navigation = computed<readonly NavigationItem[]>(() =>
    this.authService.isOwner()
      ? [...AppComponent.BASE_NAVIGATION, { label: 'Usuárias', path: '/usuarias' }]
      : AppComponent.BASE_NAVIGATION
  );

  protected readonly isAuthRoute = signal(isAuthRouteUrl(this.router.url));

  constructor() {
    this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe((event) => {
      this.isAuthRoute.set(isAuthRouteUrl(event.urlAfterRedirects));
    });
  }

  protected logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}

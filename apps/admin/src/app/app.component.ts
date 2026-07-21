import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

interface NavigationItem {
  readonly label: string;
  readonly path: string;
}

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {
  protected readonly navigation: readonly NavigationItem[] = [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Agenda', path: '/agenda' },
    { label: 'Clientes', path: '/clientes' },
    { label: 'Serviços', path: '/servicos' },
    { label: 'Lista de espera', path: '/lista-de-espera' },
    { label: 'Relacionamento', path: '/relacionamento' },
    { label: 'Configurações', path: '/configuracoes' }
  ];
}

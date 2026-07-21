import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-clients-page',
  templateUrl: './clients-page.component.html',
  styleUrl: './clients-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClientsPageComponent {}

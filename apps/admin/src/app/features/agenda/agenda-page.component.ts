import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-agenda-page',
  templateUrl: './agenda-page.component.html',
  styleUrl: './agenda-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AgendaPageComponent {}

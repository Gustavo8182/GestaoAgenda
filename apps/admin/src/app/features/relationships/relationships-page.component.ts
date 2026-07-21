import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-relationships-page',
  templateUrl: './relationships-page.component.html',
  styleUrl: './relationships-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RelationshipsPageComponent {}

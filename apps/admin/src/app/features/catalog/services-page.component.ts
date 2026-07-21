import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-services-page',
  templateUrl: './services-page.component.html',
  styleUrl: './services-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServicesPageComponent {}

import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-waitlist-page',
  templateUrl: './waitlist-page.component.html',
  styleUrl: './waitlist-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WaitlistPageComponent {}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-request-password-reset-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './request-password-reset-page.component.html',
  styleUrl: './login-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RequestPasswordResetPageComponent {
  private readonly authService = inject(AuthService);

  protected readonly submitting = signal(false);
  protected readonly resultMessage = signal<string | null>(null);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] })
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    const { email } = this.form.getRawValue();

    this.authService.requestPasswordReset(email).subscribe({
      next: (result) => {
        this.submitting.set(false);
        this.resultMessage.set(result.message);
      },
      error: () => {
        this.submitting.set(false);
        this.resultMessage.set('Não foi possível processar o pedido agora. Tente novamente em instantes.');
      }
    });
  }
}

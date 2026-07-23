import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { MemberSummary } from '../../core/membership/member-summary';
import { MembershipService } from '../../core/membership/membership.service';

@Component({
  selector: 'app-members-page',
  imports: [ReactiveFormsModule],
  templateUrl: './members-page.component.html',
  styleUrl: './members-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MembersPageComponent {
  private readonly membershipService = inject(MembershipService);
  protected readonly authService = inject(AuthService);

  protected readonly members = signal<MemberSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly updatingId = signal<string | null>(null);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  constructor() {
    this.reload();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    const { email, displayName } = this.form.getRawValue();

    this.membershipService.invite(email, displayName).subscribe({
      next: (member) => {
        this.members.update((current) => [...current, member]);
        this.form.reset();
        this.submitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error.error?.code === 'email_already_registered'
            ? 'Já existe uma usuária cadastrada com este e-mail.'
            : 'Não foi possível enviar o convite. Confira os dados informados.'
        );
      }
    });
  }

  protected disable(memberId: string): void {
    if (this.updatingId()) {
      return;
    }

    this.updatingId.set(memberId);
    this.membershipService.disable(memberId).subscribe({
      next: (updated) => {
        this.members.update((current) => current.map((m) => (m.id === updated.id ? updated : m)));
        this.updatingId.set(null);
      },
      error: () => {
        this.updatingId.set(null);
      }
    });
  }

  protected reactivate(memberId: string): void {
    if (this.updatingId()) {
      return;
    }

    this.updatingId.set(memberId);
    this.membershipService.reactivate(memberId).subscribe({
      next: (updated) => {
        this.members.update((current) => current.map((m) => (m.id === updated.id ? updated : m)));
        this.updatingId.set(null);
      },
      error: () => {
        this.updatingId.set(null);
      }
    });
  }

  private reload(): void {
    this.loading.set(true);
    this.membershipService.list().subscribe({
      next: (members) => {
        this.members.set(members);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}

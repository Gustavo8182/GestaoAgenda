export type MemberRole = 'OWNER' | 'SECRETARY' | 'SUPPORT';
export type MemberStatus = 'INVITED' | 'ACTIVE' | 'DISABLED';

export interface MemberSummary {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
  readonly role: MemberRole;
  readonly status: MemberStatus;
}

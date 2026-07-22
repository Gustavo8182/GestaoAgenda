export interface CurrentUser {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
}

export type OrganizationRole = 'OWNER' | 'SECRETARY' | 'SUPPORT';

export interface CurrentOrganization {
  readonly organizationId: string;
  readonly organizationName: string;
  readonly role: OrganizationRole;
}

export interface SessionInfo {
  readonly user: CurrentUser;
  readonly organization: CurrentOrganization;
}

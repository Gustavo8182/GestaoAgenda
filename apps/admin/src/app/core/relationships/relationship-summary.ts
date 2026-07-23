export type RelationshipStatus =
  | 'NEW_CONTACT'
  | 'IN_SERVICE'
  | 'AWAITING_RESPONSE'
  | 'PENDING_APPOINTMENT'
  | 'SCHEDULED'
  | 'FOLLOW_UP_LATER'
  | 'DID_NOT_SCHEDULE'
  | 'DO_NOT_CONTACT';

export interface RelationshipSummary {
  readonly id: string;
  readonly name: string;
  readonly phone: string;
  readonly origin: string | null;
  readonly status: RelationshipStatus;
  readonly lastInteractionAt: string;
  readonly nextAction: string | null;
  readonly nextActionAt: string | null;
  readonly responsibleName: string;
  readonly clientId: string | null;
  readonly appointmentId: string | null;
  readonly pendingContact: boolean;
}

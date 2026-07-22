export interface ClientSummary {
  readonly id: string;
  readonly name: string;
  readonly phone: string;
  readonly alternatePhone: string | null;
  readonly origin: string | null;
  readonly notes: string | null;
}

export interface CreateClientResult {
  readonly client: ClientSummary;
  readonly possibleDuplicate: boolean;
}

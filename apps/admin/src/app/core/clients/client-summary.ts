export interface ClientSummary {
  readonly id: string;
  readonly name: string;
  readonly phone: string;
}

export interface CreateClientResult {
  readonly client: ClientSummary;
  readonly possibleDuplicate: boolean;
}

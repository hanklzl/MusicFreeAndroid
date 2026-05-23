import type { ParsedEvent } from '../model/types.js';

export type ActionResult = 'success' | 'failure' | 'partial' | 'ongoing';

export interface ActionCardData {
  ruleId: string;
  icon: string;
  title: string;
  subtitle?: string;
  result: ActionResult;
  durationMs?: number;
  followups?: { arrow: string; text: string; id: string }[];
  fields: Record<string, unknown>;
  childEventIds: string[];
}

export interface ActionMatch {
  ruleId: string;
  consumedIndices: number[];
  card: ActionCardData;
}

export interface ActionRule {
  id: string;
  label: string;
  icon: string;
  /**
   * Try to match starting at events[start]. Return null if rule doesn't apply at this position.
   * If matched, return a card + list of indices to mark consumed (so subsequent rules / raw
   * fallback skip them).
   */
  match(events: ParsedEvent[], start: number): ActionMatch | null;
}

export type TimelineItem =
  | { kind: 'action'; card: ActionCardData; sortMs: number; eventIds: string[] }
  | { kind: 'raw'; event: ParsedEvent; sortMs: number };

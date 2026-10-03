/*
 * Type contracts for the ui-kit slots: the shapes ui-kit implements and
 * core-api's tokens.ts accepts, kept separate so either side can be read
 * without pulling in the other.
 */
import { Type } from '@angular/core';
import type { Datatype, StateMachineResponse, TriggerKind } from './models';

/** A single diagnostic marker to surface in a Turtle editor adapter. */
export interface EditorDiagnostic {
  from: number;
  to: number;
  message: string;
  severity: 'error' | 'warning' | 'info';
}

/** The adapter contract a Turtle editor implementation must satisfy. */
export interface TextEditorAdapter {
  mount(host: HTMLElement, initialValue: string, onChange: (value: string) => void): Promise<void>;
  getValue(): string;
  setValue(value: string): void;
  setDiagnostics(diagnostics: readonly EditorDiagnostic[]): void;
  dispose(): void;
}

/** Factory that produces a fresh TextEditorAdapter for the TURTLE_EDITOR slot. */
export type TextEditorFactory = () => TextEditorAdapter;

/** A single point along a graph edge's path. */
export interface GraphPoint {
  x: number;
  y: number;
}

/** A laid-out node in a state machine graph. */
export interface GraphNode {
  id: string;
  label: string;
  x: number;
  y: number;
  width: number;
  height: number;
  initial: boolean;
}

/** A laid-out edge in a state machine graph. */
export interface GraphEdge {
  id: string;
  from: string;
  to: string;
  points: readonly GraphPoint[];
  label: string;
  trigger: TriggerKind;
  hasGuard: boolean;
}

/** The computed layout of a state machine graph. */
export interface GraphLayoutResult {
  width: number;
  height: number;
  nodes: readonly GraphNode[];
  edges: readonly GraphEdge[];
}

/** The layout engine contract for the GRAPH_LAYOUT slot. */
export interface GraphLayout {
  layout(stateMachine: StateMachineResponse): Promise<GraphLayoutResult>;
}

/**
 * Keys: a Datatype, 'RELATIONSHIP', or a custom renderer key. Values: standalone component classes.
 */
export type FieldRendererKey = Datatype | 'RELATIONSHIP' | (string & {});

/** The map of field renderer keys to standalone component classes for the FIELD_RENDERERS slot. */
export type FieldRendererMap = Readonly<Partial<Record<FieldRendererKey, Type<unknown>>>>;

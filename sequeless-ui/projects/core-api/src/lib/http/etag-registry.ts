import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ETagRegistry {
  private readonly versions = new Map<string, string>();

  set(id: string, etag: string): void {
    this.versions.set(id, stripQuotes(etag));
  }

  get(id: string): string | undefined {
    return this.versions.get(id);
  }

  clear(id: string): void {
    this.versions.delete(id);
  }
}

function stripQuotes(value: string): string {
  if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
    return value.slice(1, -1);
  }
  return value;
}

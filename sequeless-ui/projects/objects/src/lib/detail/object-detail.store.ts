import { inject, Injectable, signal } from '@angular/core';
import {
  ApiError,
  BusinessObjectResponse,
  ObjectsClient,
  TypeDetailResponse,
  TypesClient,
} from '@sequeless/core-api';
import { firstValueFrom } from 'rxjs';

interface Target {
  typeName: string;
  id: string;
}

/**
 * Holds the type metadata and the object being viewed or edited for one detail screen. A failed
 * load or refetch sets `error` and keeps whatever object was already on screen.
 */
@Injectable()
export class ObjectDetailStore {
  private readonly types = inject(TypesClient);
  private readonly objects = inject(ObjectsClient);
  private target: Target | null = null;

  readonly type = signal<TypeDetailResponse | null>(null);
  readonly object = signal<BusinessObjectResponse | null>(null);
  readonly error = signal<ApiError | null>(null);

  async load(typeName: string, id: string): Promise<void> {
    this.target = { typeName, id };
    this.error.set(null);
    try {
      const [type, object] = await Promise.all([
        firstValueFrom(this.types.describe(typeName)),
        firstValueFrom(this.objects.get(typeName, id)),
      ]);
      this.type.set(type);
      this.object.set(object);
    } catch (err) {
      this.error.set(err as ApiError);
    }
  }

  async refetch(): Promise<BusinessObjectResponse | null> {
    if (this.target === null) {
      return this.object();
    }
    const { typeName, id } = this.target;
    try {
      const object = await firstValueFrom(this.objects.get(typeName, id));
      this.error.set(null);
      this.object.set(object);
      return object;
    } catch (err) {
      this.error.set(err as ApiError);
      return this.object();
    }
  }

  set(object: BusinessObjectResponse): void {
    this.object.set(object);
  }
}

import { AsyncLocalStorage } from 'async_hooks';

interface RequestContext {
  correlationId: string | null;
  tenantId: string | null;
}

export const requestContextStore = new AsyncLocalStorage<RequestContext>();

export function getRequestContext(): RequestContext {
  return requestContextStore.getStore() ?? { correlationId: null, tenantId: null };
}

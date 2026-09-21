import { AsyncLocalStorage } from 'async_hooks';

interface RequestContext {
  correlationId: string | null;
  tenantId: string | null;
}

export const requestContextStore = new AsyncLocalStorage<RequestContext>();

/** Returns the current request's correlation/tenant context, or nulls when called outside a request scope. */
export function getRequestContext(): RequestContext {
  return requestContextStore.getStore() ?? { correlationId: null, tenantId: null };
}

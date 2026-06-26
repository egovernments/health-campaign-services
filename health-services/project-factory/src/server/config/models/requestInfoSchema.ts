import { z } from "zod";

export const roleSchema = z.object({
  id: z.number().nullable().optional(),
  name: z.string().nullable().optional(),
  code: z.string().nullable().optional(),
  tenantId: z.string().nullable().optional(),
});

export const plainAccessRequestSchema = z.object({
  recordId: z.string().nullable().optional(),
  plainRequestFields: z.array(z.string()).nullable().optional(),
});

export const userInfoSchema = z.object({
  id: z.number().nullable().optional(),
  userName: z.string().nullable().optional(),
  name: z.string().nullable().optional(),
  type: z.string().nullable().optional(),
  mobileNumber: z.string().nullable().optional(),
  emailId: z.string().nullable().optional(),
  roles: z.array(roleSchema).nullable().optional(),
  tenantId: z.string().optional(),
  uuid: z.string().nullable().optional(),
});

export const requestInfoSchema = z.object({
  apiId: z.string().nullable().optional(),
  ver: z.string().nullable().optional(),
  ts: z.number().nullable().optional(),
  action: z.string().nullable().optional(),
  did: z.string().nullable().optional(),
  key: z.string().nullable().optional(),
  msgId: z.string().optional(),
  authToken: z.string().nullable().optional(),
  correlationId: z.string().nullable().optional(),
  plainAccessRequest: plainAccessRequestSchema.nullable().optional(),
  userInfo: userInfoSchema.nullable().optional(),
});

export type Role = z.infer<typeof roleSchema>;
export type PlainAccessRequest = z.infer<typeof plainAccessRequestSchema>;
export type UserInfo = z.infer<typeof userInfoSchema>;
export type RequestInfo = z.infer<typeof requestInfoSchema>;

/**
 * Creates a new RequestInfo with overridden userInfo fields.
 * Does NOT mutate the original requestInfo — returns a new object.
 */
export function withUserInfo(requestInfo: RequestInfo, overrides: Partial<UserInfo>): RequestInfo {
  return {
    ...requestInfo,
    userInfo: { ...requestInfo.userInfo, ...overrides },
  };
}

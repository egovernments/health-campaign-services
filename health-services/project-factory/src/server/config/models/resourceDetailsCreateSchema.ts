import { z } from 'zod';

export const resourceDetailsCreateSchema = z.object({
  tenantId: z.string().min(1, "tenantId is required"),
  campaignId: z.string().min(1).optional().nullable(),
  campaignNumber: z.string().min(1).optional().nullable(),
  type: z.string().min(1, "type is required").max(128, "type must be 128 characters or less"),
  parentResourceId: z.string().max(64).optional().nullable(),
  fileStoreId: z.string().min(1, "fileStoreId is required"),
  filename: z.string().max(256).optional().nullable(),
  additionalDetails: z.record(z.any()).optional()
}).superRefine((data, ctx) => {
  if (!data.campaignId && !data.campaignNumber) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Either campaignId or campaignNumber must be provided",
      path: ["campaignId"],
    });
  }
});

export type ResourceDetailsCreateInput = z.infer<typeof resourceDetailsCreateSchema>;

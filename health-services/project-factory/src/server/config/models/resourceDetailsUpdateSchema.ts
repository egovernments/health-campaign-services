import { z } from 'zod';

export const resourceDetailsUpdateSchema = z.object({
  id: z.string().min(1, "id is required"),
  tenantId: z.string().min(1, "tenantId is required"),
  campaignId: z.string().min(1).optional().nullable(),
  campaignNumber: z.string().min(1).optional().nullable(),
  fileStoreId: z.string().min(1, "fileStoreId is required"),
  filename: z.string().max(256).optional().nullable()
}).superRefine((data, ctx) => {
  if (!data.campaignId && !data.campaignNumber) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Either campaignId or campaignNumber must be provided",
      path: ["campaignId"],
    });
  }
});

export type ResourceDetailsUpdateInput = z.infer<typeof resourceDetailsUpdateSchema>;

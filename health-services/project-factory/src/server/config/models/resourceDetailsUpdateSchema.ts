import { z } from 'zod';

export const resourceDetailsUpdateSchema = z.object({
  id: z.string().min(1, "id is required"),
  tenantId: z.string().min(1, "tenantId is required"),
  campaignId: z.string().min(1).optional().nullable(),
  campaignNumber: z.string().min(1).optional().nullable(),
  fileStoreId: z.string().min(1, "fileStoreId is required"),
  filename: z.string().max(256).optional().nullable()
});

export type ResourceDetailsUpdateInput = z.infer<typeof resourceDetailsUpdateSchema>;

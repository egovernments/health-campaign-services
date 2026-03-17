import { z } from 'zod';

export const resourceDetailsCriteriaSchema = z.object({
  tenantId: z.string().min(1, "tenantId is required"),
  campaignId: z.string().min(1, "campaignId is required"),
  type: z.array(z.string()).optional(),
  ids: z.array(z.string()).optional(),
  parentResourceId: z.string().optional().nullable(),
  status: z.array(z.string()).optional(),
  isActive: z.boolean().optional()
});

export const paginationSchema = z.object({
  limit: z.number().int().positive().max(500).optional(),
  offset: z.number().int().min(0).optional(),
  sortBy: z.string().optional(),
  sortOrder: z.enum(['ASC', 'DESC']).optional()
});

export type ResourceDetailsCriteria = z.infer<typeof resourceDetailsCriteriaSchema>;
export type Pagination = z.infer<typeof paginationSchema>;

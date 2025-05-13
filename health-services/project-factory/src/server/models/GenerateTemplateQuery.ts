import { z } from 'zod';

export const generateTemplateQuerySchema = z.object({
    type: z.string().min(1, { message: 'type must be a non-empty string' }).refine(val => val !== null, { message: 'type cannot be null' }),
    tenantId: z.string().min(1, { message: 'tenantId must be a non-empty string' }).refine(val => val !== null, { message: 'tenantId cannot be null' }),
    hierarchyType: z.string().min(1, { message: 'hierarchyType must be a non-empty string' }).refine(val => val !== null, { message: 'hierarchyType cannot be null' }),
    campaignId: z.string().min(1, { message: 'campaignId must be a non-empty string' }).refine(val => val !== null, { message: 'campaignId cannot be null' }),
});

// TypeScript interface (can be omitted if you're using z.infer)
export type GenerateTemplateQuery = z.infer<typeof generateTemplateQuerySchema>;

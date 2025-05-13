import { z } from 'zod';

// 1. Define the Zod schema
export const resourceDetailsSchema = z.object({
    type: z.string().min(1, { message: 'type must be a non-empty string' }).refine(val => val !== null, { message: 'type cannot be null' }),
    hierarchyType: z.string().min(1, { message: 'hierarchyType must be a non-empty string' }).refine(val => val !== null, { message: 'hierarchyType cannot be null' }),
    tenantId: z.string().min(1, { message: 'tenantId must be a non-empty string' }).refine(val => val !== null, { message: 'tenantId cannot be null' }),
    fileStoreId: z.string().min(1, { message: 'fileStoreId must be a non-empty string' }).refine(val => val !== null, { message: 'fileStoreId cannot be null' }),
    campaignId: z.string().min(1, { message: 'campaignId must be a non-empty string' }).refine(val => val !== null, { message: 'campaignId cannot be null' }),
    additionalDetails: z.record(z.any()).optional(),
});

// 2. Export the TypeScript interface inferred from the schema
export type ResourceDetails = z.infer<typeof resourceDetailsSchema>;
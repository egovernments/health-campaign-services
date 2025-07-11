import { z } from 'zod';

// 1. Define the Zod schema
const requiredString = (fieldName: string) =>
    z.string({
        required_error: `${fieldName} is required`, // For undefined
        invalid_type_error: `${fieldName} should be a string` // For null or wrong types
    }).min(1, { message: `${fieldName} must be a non-empty string` }); // For empty string

export const resourceDetailsSchema = z.object({
    type: requiredString('type'),
    hierarchyType: requiredString('hierarchyType'),
    tenantId: requiredString('tenantId'),
    fileStoreId: requiredString('fileStoreId'),
    campaignId: requiredString('campaignId'),
    additionalDetails: z.record(z.any()).optional(),
});


// 2. Export the TypeScript interface inferred from the schema
export type ResourceDetails = z.infer<typeof resourceDetailsSchema>;
import { z } from 'zod';

const requiredString = (fieldName: string) =>
    z.string({
        required_error: `${fieldName} is required`,
        invalid_type_error: `${fieldName} should be a string`
    }).min(1, { message: `${fieldName} must be a non-empty string` });

export const resourceDetailsSchema = z.object({
    type: requiredString('type'),
    hierarchyType: requiredString('hierarchyType'),
    tenantId: requiredString('tenantId'),
    fileStoreId: requiredString('fileStoreId'),
    campaignId: requiredString('campaignId'),
    additionalDetails: z.record(z.any()).optional(),
    parentReferenceId: z.string().optional(),
    status : z.string().optional(),
    requestInfo: z.record(z.any()).optional()
});

export type ResourceDetails = z.infer<typeof resourceDetailsSchema>;
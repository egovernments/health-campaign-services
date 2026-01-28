import { z } from 'zod';

const requiredString = (fieldName: string) =>
    z.string({
        required_error: `${fieldName} is required`,         // triggers on undefined
        invalid_type_error: `${fieldName} should be a string`,     // triggers on null or wrong type
    }).min(1, { message: `${fieldName} must be a non-empty string` }); // triggers on empty string

export const generateTemplateQuerySchema = z.object({
    type: requiredString('type'),
    tenantId: requiredString('tenantId'),
    hierarchyType: requiredString('hierarchyType'),
    campaignId: requiredString('campaignId')
});

// TypeScript interface (can be omitted if you're using z.infer)
export type GenerateTemplateQuery = z.infer<typeof generateTemplateQuerySchema>;

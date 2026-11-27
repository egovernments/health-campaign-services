import { buildCampaignOrderByClause } from '../utils/campaignSearchQueryUtils';

describe('buildCampaignOrderByClause', () => {
    describe('values the console sends today', () => {
        it('keeps a camelCase column, lower-cased as Postgres folds it', () => {
            expect(buildCampaignOrderByClause('createdTime', 'desc')).toBe('ORDER BY createdtime DESC\n');
        });

        it('keeps lastModifiedTime and campaignName', () => {
            expect(buildCampaignOrderByClause('lastModifiedTime', 'asc')).toBe('ORDER BY lastmodifiedtime ASC\n');
            expect(buildCampaignOrderByClause('campaignName', 'asc')).toBe('ORDER BY campaignname ASC\n');
        });

        it('orders without a direction when sortOrder is absent', () => {
            expect(buildCampaignOrderByClause('createdTime', undefined)).toBe('ORDER BY createdtime\n');
        });
    });

    describe('values that must never reach SQL', () => {
        it('drops ordering for an injected sortBy', () => {
            expect(buildCampaignOrderByClause('(CASE WHEN (SELECT 1)=1 THEN createdtime ELSE id END)', 'asc')).toBe('');
        });

        it('drops ordering for an unknown column', () => {
            expect(buildCampaignOrderByClause('notAColumn', 'asc')).toBe('');
        });

        it('ignores an unsafe sortOrder but keeps the valid column', () => {
            expect(buildCampaignOrderByClause('createdTime', 'asc; DROP TABLE x')).toBe('ORDER BY createdtime\n');
        });

        it('returns no ordering when sortBy is missing or not a string', () => {
            expect(buildCampaignOrderByClause(undefined, 'asc')).toBe('');
            expect(buildCampaignOrderByClause(42, 'asc')).toBe('');
        });
    });
});

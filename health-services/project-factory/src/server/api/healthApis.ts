import { httpRequest } from "../utils/request";
import { defaultRequestInfo } from "./coreApis";
import config from "../config";
import { throwError } from "../utils/genericUtils";
import { logger } from "../utils/logger";

export async function fetchProductVariants(pvarIds: string[]) {
    const CHUNK_SIZE = 100;
    const allProductVariants: any[] = [];
    const params: any = { limit: CHUNK_SIZE, offset: 0, tenantId: defaultRequestInfo?.RequestInfo?.userInfo?.tenantId };

    for (let i = 0; i < pvarIds.length; i += CHUNK_SIZE) {
        const chunk = pvarIds.slice(i, i + CHUNK_SIZE);
        try {
            const response = await httpRequest(
                config.host.productHost + config.paths.productVariantSearch,
                { ProductVariant: { id: chunk }, ...defaultRequestInfo },
                params
            );
            allProductVariants.push(...response?.ProductVariant || []);
        } catch (error: any) {
            logger.error("Error during product variant fetch");
            throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", `Some error occurred while fetching product variants. ${error?.message}`);
        }
    }

    return allProductVariants;  // Return the fetched product variants
}

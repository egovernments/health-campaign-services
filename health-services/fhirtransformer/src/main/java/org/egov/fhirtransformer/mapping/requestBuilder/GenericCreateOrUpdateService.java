package org.egov.fhirtransformer.mapping.requestBuilder;

import org.egov.common.contract.request.RequestInfo;
import org.egov.fhirtransformer.common.Constants;
import org.egov.fhirtransformer.utils.BundleBuilder;
import org.egov.fhirtransformer.utils.MapUtils;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class GenericCreateOrUpdateService {

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingBiConsumer<T, U> {
        void accept(T t, U u) throws Exception;
    }

    /**
     * Generic orchestration for processing entity maps: checks existing IDs and delegates create/update.
     * @param entityMap map of id -> entity
     * @param checkExistingFn function that accepts a list of ids and returns map of new/existing ids
     * @param createOrUpdateFn bi-consumer that accepts the new/existing map and the original entity map and performs create/update
     * @param errorMessage base error message used when wrapping exceptions
     * @param <T> entity type
     * @return results metrics map
     * @throws Exception if underlying functions throw
     */
    public <T> HashMap<String, Integer> process(HashMap<String, T> entityMap,
                                                ThrowingFunction<List<String>, HashMap<String, List<String>>> checkExistingFn,
                                                ThrowingBiConsumer<HashMap<String, List<String>>, HashMap<String, T>> createOrUpdateFn,
                                                String errorMessage) throws Exception {
        HashMap<String, Integer> results = new HashMap<>();
        if (entityMap == null || entityMap.isEmpty()) {
            return results;
        }

        try {
            List<String> idList = new ArrayList<>(entityMap.keySet());
            if (!idList.isEmpty()) {
                HashMap<String, List<String>> newAndExistingIdsMap = checkExistingFn.apply(idList);
                createOrUpdateFn.accept(newAndExistingIdsMap, entityMap);
                results.put(Constants.TOTAL_PROCESSED, entityMap.size());
                return BundleBuilder.fetchMetrics(results, newAndExistingIdsMap);
            }
        } catch (Exception e) {
            throw new Exception(errorMessage + ": " + e.getMessage());
        }
        return results;
    }

    /**
     * Overloaded generic orchestration that accepts a function to fetch existing IDs (returns List<String>)
     * and separate create/update functions that act on lists of entities and target URLs.
     * This centralizes the common check-and-split logic so callers only supply entity-specific fetchers and creators.
     */
    public <T> HashMap<String, Integer> process(HashMap<String, T> entityMap,
                                                ThrowingFunction<List<String>, List<String>> fetchExistingIdsFn,
                                                ThrowingBiConsumer<List<T>, String> createFn,
                                                ThrowingBiConsumer<List<T>, String> updateFn,
                                                String createUrl,
                                                String updateUrl,
                                                RequestInfo requestInfo,
                                                String errorMessage) throws Exception {
        HashMap<String, Integer> results = new HashMap<>();
        if (entityMap == null || entityMap.isEmpty()) {
            return results;
        }

        try {
            List<String> idList = new ArrayList<>(entityMap.keySet());
            if (!idList.isEmpty()) {
                // fetch existing ids using caller-provided function
                List<String> existingIds = fetchExistingIdsFn.apply(idList);

                // compute new & existing ids map using shared util
                List<String> newIdsMutable = new ArrayList<>(idList);
                HashMap<String, List<String>> newAndExistingIdsMap = MapUtils.splitNewAndExistingIDS(newIdsMutable, existingIds);

                // prepare and call create
                if (newAndExistingIdsMap.containsKey(Constants.NEW_IDS)) {
                    List<T> toCreate = new ArrayList<>();
                    for (String id : newAndExistingIdsMap.get(Constants.NEW_IDS)) {
                        toCreate.add(entityMap.get(id));
                    }
                    if (!toCreate.isEmpty() && createFn != null) {
                        createFn.accept(toCreate, createUrl);
                    }
                }

                // prepare and call update
                if (newAndExistingIdsMap.containsKey(Constants.EXISTING_IDS)) {
                    List<T> toUpdate = new ArrayList<>();
                    for (String id : newAndExistingIdsMap.get(Constants.EXISTING_IDS)) {
                        toUpdate.add(entityMap.get(id));
                    }
                    if (!toUpdate.isEmpty() && updateFn != null) {
                        updateFn.accept(toUpdate, updateUrl);
                    }
                }

                results.put(Constants.TOTAL_PROCESSED, entityMap.size());
                return BundleBuilder.fetchMetrics(results, newAndExistingIdsMap);
            }
        } catch (Exception e) {
            throw new Exception(errorMessage + ": " + e.getMessage());
        }
        return results;
    }
}

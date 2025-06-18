package org.egov.id.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdRecordBulkRequest;

import org.springframework.stereotype.Service;


import static org.egov.common.utils.CommonUtils.*;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class EnrichmentService {


    public void update(List<IdRecord> validIdRecords, IdRecordBulkRequest request) {
        log.info("starting the enrichment for update idRecords");
        Map<String, IdRecord> iMap = getIdToObjMap(validIdRecords);
        log.info("enriching individuals for update");
        enrichForUpdate(iMap, request);
        log.info("completed the enrichment for update idRecords");
    }

    public void enrichStatusForUpdate(List<IdRecord> validIdRecords , IdRecordBulkRequest request, String status) {
        for (IdRecord idRecord : validIdRecords) {
            idRecord.setStatus(status);
        }
        update(validIdRecords, request);
    }

}
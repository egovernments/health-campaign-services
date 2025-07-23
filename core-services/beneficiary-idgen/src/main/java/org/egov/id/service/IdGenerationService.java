package org.egov.id.service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.idgen.*;
import org.egov.common.utils.ResponseInfoUtil;
import org.egov.id.config.PropertiesManager;
import org.egov.id.producer.IdGenProducer;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.jdbc.core.JdbcTemplate;


/**
 * Description : IdGenerationService have methods related to the IdGeneration
 *
 * @author Pavan Kumar Kamma
 */
@Service
@Slf4j
public class IdGenerationService {

    @Autowired
    DataSource dataSource;

    @Autowired
    PropertiesManager propertiesManager;

    @Autowired
    private MdmsService mdmsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // by default 'idformat' will be taken from MDMS. Change value of 'ismdms.on' to 'false'
    // in application.properties to get data from DB instead.
    @Value("${idformat.from.mdms}")
    public boolean idFormatFromMDMS;


    //By default the auto create sequence is disabled
    @Value("${autocreate.new.seq}")
    public boolean autoCreateNewSeq;

    @Autowired
    private IdGenProducer idGenProducer;

    //default count value
    public Integer defaultCount = 1;

    @Value("${id.pool.seq.code}")
    public String idPoolName;

    @Value("${idgen.random.buffer:5}")
    public Integer defaultBufferPercentage;

    /**
     * Maximum batch size for synchronous ID generation operations.
     * This is used to limit the number of IDs generated in a single request for performance and reliability.
     */
    @Value("${id.pool.create.max.batch.size:1000}")
    private Integer MAX_BATCH_SIZE;

    /**
     * Maximum batch size for asynchronous Kafka operations.
     * This is used to limit the number of IDs generated in a single batch for performance and reliability.
     */
    @Value("${id.pool.async.create.max.batch.size:50000}")
    private Integer MAX_ASYNC_KAFKA_BATCH_SIZE;

    private static final Pattern RANDOM_PATTERN = Pattern.compile("\\[d\\{\\d+}]");

    /**
     * Padding length for sequence numbers in generated IDs.
     * This ensures that sequence numbers are zero-padded to a fixed length for consistency.
     */
    @Value("${id.pool.padding.length:12}")
    private Integer paddingLength;

    /**
     * Description : This method to generate idGenerationResponse
     *
     * @param idGenerationRequest
     * @return idGenerationResponse
     * @throws Exception
     */

    public IdGenerationResponse generateIdResponse(IdGenerationRequest idGenerationRequest) throws Exception {

        RequestInfo requestInfo = idGenerationRequest.getRequestInfo();
        List<IdRequest> idRequests = idGenerationRequest.getIdRequests();
        List<IdResponse> idResponses = new LinkedList<>();

        IdGenerationResponse idGenerationResponse = new IdGenerationResponse();

        for (IdRequest idRequest : idRequests) {
            List<String> generatedId = generateIdFromIdRequest(idRequest, requestInfo);
            for (String ListOfIds : generatedId) {
                IdResponse idResponse = new IdResponse();
                idResponse.setId(ListOfIds);
                idResponses.add(idResponse);
            }
        }
        idGenerationResponse.setIdResponses(idResponses);
        idGenerationResponse.setResponseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true));

        return idGenerationResponse;

    }

    /**
     * Description : This method to generate ID pool in batches and persist them to Kafka.
     * It handles chunking of requests, validates input, and returns a response with results.
     *
     * @param request the ID pool generation request containing batch details
     * @return IDPoolGenerationResponse containing results of the ID pool creation
     */
    public IDPoolGenerationResponse generateIDPool(IDPoolGenerationRequest request) {
        List<BatchRequest> batches = request.getBatchRequestList();

        // Validate input
        if (batches == null || batches.isEmpty()) {
            throw new CustomException("EMPTY REQUEST", "Batch list is empty.");
        }

        List<IDPoolCreationResult> results = new ArrayList<>();

        // Iterate over all batch requests
        for (BatchRequest batch : batches) {
            int fullSize = batch.getBatchSize(); // Total number of IDs requested
            int sent = 0;
            // Chunk and send large batch into smaller parts to Kafka
            while (sent < fullSize) {
                // Calculate the size of the current chunk
                int chunkSize = Math.min(MAX_ASYNC_KAFKA_BATCH_SIZE, fullSize - sent);
                // Build Kafka request for the current chunk
                IDPoolGenerationKafkaRequest kafkaRequest = IDPoolGenerationKafkaRequest.builder()
                        .tenantId(batch.getTenantId())
                        .batchSize(chunkSize)
                        .requestInfo(request.getRequestInfo())
                        .build();

                // Push the chunked request to Kafka
                idGenProducer.push(propertiesManager.getIdPoolAsyncCreateTopic(), kafkaRequest);
                sent += chunkSize;
            }
            // Add a response message for this tenant
            results.add(IDPoolCreationResult.builder()
                    .tenantId(batch.getTenantId())
                    .message("ID Generation is chunked and queued")
                    .build());
        }

        // Return a combined response for all tenants
        return IDPoolGenerationResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .idPoolCreateResponses(results)
                .build();
    }

    /**
     * Fetches the ID format string for the specified tenant and batch size.
     * Validates the configured pool name and handles exceptions from the ID generation service.
     *
     * @param tenantId the tenant identifier
     * @param batchSize the requested batch size of IDs
     * @param requestInfo contextual request info for audit and tracing
     * @return the ID format string configured for the tenant
     * @throws CustomException if configuration is missing or fetching format fails
     * @throws IllegalArgumentException if fetched ID format is null or empty
     */
    private String fetchIdFormat(String tenantId, Integer batchSize, RequestInfo requestInfo) {
        log.info("Fetching ID format for tenantId={}, batchSize={}", tenantId, batchSize);

        if (ObjectUtils.isEmpty(idPoolName)) {
            log.error("Configuration Error - 'id.pool.seq.code' is not set.");
            throw new CustomException("Configuration Error:", "Please configure the 'id.pool.seq.code' on the service level.");
        }

        IdRequest tempRequest = new IdRequest(idPoolName, tenantId, null, batchSize);
        String idFormat;

        try {
            idFormat = getIdFormatFinal(tempRequest, requestInfo);
            log.info("Received ID format: {}", idFormat);
        } catch (Exception e) {
            log.error("Exception while fetching ID format from ID generation service for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException(e);
        }

        if (ObjectUtils.isEmpty(idFormat)) {
            log.error("ID format cannot be null or empty for tenant {}", tenantId);
            throw new IllegalArgumentException("ID format cannot be null or empty");
        }

        return idFormat;
    }

    /**
     * Adjusts the batch size by adding a buffer if the ID format contains a random pattern.
     * This compensates for possible duplicates or retries in ID generation.
     *
     * @param batchSize the original requested batch size
     * @param idFormat the ID format string to check for randomness
     * @return adjusted batch size, increased by the configured buffer percentage if random pattern detected, otherwise original batch size
     */
    private Integer adjustBatchSizeIfRandom(Integer batchSize, String idFormat) {
        log.info("Adjusting batch size if ID format is random. Batch size: {}, ID format: {}", batchSize, idFormat);

        Matcher randomMatcher = RANDOM_PATTERN.matcher(idFormat);
        if (randomMatcher.find()) {
            int adjusted = (int) Math.ceil(batchSize * (1 + defaultBufferPercentage / 100.0));
            log.info("Detected random pattern in ID format. Adjusted batch size: {} (Buffer: {}%)", adjusted, defaultBufferPercentage);
            return adjusted;
        }

        log.info("No random pattern detected. Returning original batch size: {}", batchSize);
        return batchSize;
    }

    /**
     * Handles asynchronous ID pool generation requests by fetching the ID format,
     * generating IDs in batches, and persisting them to Kafka for further processing.
     * Validates the batch size and handles exceptions gracefully.
     *
     * @param request the ID pool generation request containing tenant ID, batch size, and request info
     */
    public void handleAsyncIdPoolRequest(IDPoolGenerationKafkaRequest request) {
        try {
            String tenantId = request.getTenantId();
            Integer batchSize = request.getBatchSize();
            RequestInfo requestInfo = request.getRequestInfo();

            // Validate batch size
            if (batchSize <= 0) {
                throw new CustomException("INVALID_BATCH_SIZE", "Batch size must be > 0");
            }

            // Fetch ID format and adjust batch size if necessary
            String idFormat = fetchIdFormat(tenantId, batchSize, requestInfo);
            // Adjust batch size if ID format contains random patterns
            Integer adjustedSize = adjustBatchSizeIfRandom(batchSize, idFormat);
            IdRequest idRequest = new IdRequest(idPoolName, tenantId, null, adjustedSize);
            // Generate IDs
            List<String> generatedIds = generateIds(idRequest, requestInfo);
            // Persist generated IDs to Kafka
            persistToKafka(requestInfo, generatedIds, tenantId);
            log.info("Async ID pool generated and sent to Kafka for tenant {}", tenantId);
        } catch (Exception e) {
            log.error("Error in async ID pool generation", e);
            // Optional: push to DLQ or retry topic
        }
    }

    /**
     * Generates a list of IDs by invoking the underlying ID generation service.
     * Wraps exceptions to provide consistent error handling.
     *
     * @param idRequest the ID request containing pool name, tenant, and batch size details
     * @param requestInfo contextual request info for auditing and logging
     * @return list of generated ID strings
     * @throws RuntimeException if ID generation fails
     */
    private List<String> generateIds(IdRequest idRequest, RequestInfo requestInfo) {
        try {
            return generateIdFromIdRequest(idRequest, requestInfo);
        } catch (Exception e) {
            log.error("Error generating IDs: ", e);
            throw new RuntimeException("Error generating IDs", e);
        }
    }

    /**
     * Persists generated IDs asynchronously by sending them in batches to a configured Kafka topic.
     * Uses a buffer of maximum batch size for efficiency.
     *
     * @param requestInfo contextual request info for audit fields in generated ID records
     * @param generatedIds list of generated ID strings to persist
     * @param tenantId tenant identifier for which IDs are generated
     */
    private void persistToKafka(RequestInfo requestInfo, List<String> generatedIds, String tenantId) {
        log.trace("Starting Kafka persistence for generated IDs. Tenant ID: {}, Total IDs: {}", tenantId, generatedIds.size());

        List<IdRecord> buffer = new ArrayList<>(MAX_BATCH_SIZE);

        for (int i = 0; i < generatedIds.size(); i++) {
            String generatedId = generatedIds.get(i);
            IdRecord idRecord = IdRecord.builder()
                    .tenantId(tenantId)
                    .id(generatedId)
                    .auditDetails(
                            AuditDetails.builder()
                                    .createdBy(requestInfo.getUserInfo().getUuid())
                                    .createdTime(System.currentTimeMillis())
                                    .build()
                    )
                    .rowVersion(1)
                    .build();
            buffer.add(idRecord);

            // Send batch when buffer is full or last element reached
            if (buffer.size() == MAX_BATCH_SIZE || i == generatedIds.size() - 1) {
                log.debug("Sending batch of {} IDs to Kafka topic: {}", buffer.size(), propertiesManager.getSaveIdPoolTopic());
                sendBatch(propertiesManager.getSaveIdPoolTopic(), new ArrayList<>(buffer));
                buffer.clear();
            }
        }

        log.debug("Completed Kafka persistence for {} generated IDs.", generatedIds.size());
    }

    /**
     * Sends a batch of ID records to the specified Kafka topic.
     * Logs errors on failure and can be extended to support retries or dead-letter queues.
     *
     * @param topic the Kafka topic to send the message to
     * @param entries the list of ID records to send as payload
     */
    private void sendBatch(String topic, List<IdRecord> entries) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("idPool", entries);
            idGenProducer.push(topic, payload);

        } catch (Exception e) {
            log.error("Kafka publish failed", e);
            // Optional: Add retry logic or DLQ fallback here
        }
    }

    /**
     * Description : This method to generate id
     *
     * @param idRequest
     * @param requestInfo
     * @return generatedId
     * @throws Exception
     */
    private List generateIdFromIdRequest(IdRequest idRequest, RequestInfo requestInfo) throws Exception {

        List<String> generatedId = new LinkedList<>();
        boolean autoCreateNewSeqFlag = false;
        if (!ObjectUtils.isEmpty(idRequest.getIdName()))
        {
            // If IDName is specified then check if it is defined in MDMS
            String idFormat = getIdFormatFinal(idRequest, requestInfo);

            // If the idname is defined then the format should be used
            // else fallback to the format in the request itself
            if (!ObjectUtils.isEmpty(idFormat)){
                idRequest.setFormat(idFormat);
                autoCreateNewSeqFlag=true;
            }else if(ObjectUtils.isEmpty(idFormat)){
                autoCreateNewSeqFlag=false;
            }
        }

        if (ObjectUtils.isEmpty(idRequest.getFormat()))
            throw new CustomException("ID_NOT_FOUND",
                    "No Format is available in the MDMS for the given name and tenant");

        return getFormattedId(idRequest, requestInfo,autoCreateNewSeqFlag);
    }

    /**
     * Description : This method to generate Id when format is unknown and select MDMS or DB.
     *
     * @param idRequest
     * @param requestInfo
     * @return generatedId
     * @throws Exception
     */
    private String getIdFormatFinal(IdRequest idRequest, RequestInfo requestInfo) throws Exception {

        String idFormat = null;
        try{
            if (idFormatFromMDMS == true) {
                idFormat = mdmsService.getIdFormat(requestInfo, idRequest); //from MDMS
            } else {
                idFormat = getIdFormatfromDB(idRequest, requestInfo); //from DB
            }
        }catch(Exception ex){
            if(ObjectUtils.isEmpty(idFormat)){
                throw new CustomException("ID_NOT_FOUND",
                        "No Format is available in the MDMS for the given name and tenant");
            }
            log.error("Format returned NULL from both MDMS and DB",ex);
        }
        return idFormat;
    }

    /**
     * Description : This method to retrieve Id format from DB
     *
     * @param idRequest
     * @param requestInfo
     * @return idFormat
     * @throws Exception
     */
    private String getIdFormatfromDB(IdRequest idRequest, RequestInfo requestInfo) throws Exception {
        // connection and prepared statement

        String idFormat = null;
        try {
            String idName = idRequest.getIdName();
            String tenantId = idRequest.getTenantId();
            // select the id format from the id generation table
            StringBuffer idSelectQuery = new StringBuffer();
            idSelectQuery.append("SELECT format FROM id_generator ").append(" WHERE idname=? and tenantid=?");

           idFormat = jdbcTemplate.queryForObject(idSelectQuery.toString(), String.class, idName, tenantId);
        } catch (Exception ex){
            log.error("SQL error while trying to retrive format from DB",ex);
        }
        return idFormat;
    }

    /**
     * Description : This method to generate Id when format is known
     *
     * @param idRequest
     * @param requestInfo
     * @return formattedId
     * @throws Exception
     */

    private List<String> getFormattedId(IdRequest idRequest, RequestInfo requestInfo, boolean autoCreateNewSeqFlag) throws Exception {
        List<String> idFormatList = new LinkedList<>();
        String idFormat = idRequest.getFormat();

        try{
            if (!ObjectUtils.isEmpty(idFormat.trim()) && !ObjectUtils.isEmpty(idRequest.getTenantId())) {
                idFormat = idFormat.replace("[tenantid]", idRequest.getTenantId());
                idFormat = idFormat.replace("[tenant_id]", idRequest.getTenantId().replace(".", "_"));
                idFormat = idFormat.replace("[TENANT_ID]", idRequest.getTenantId().replace(".", "_").toUpperCase());
            }
        }catch (Exception ex){
            if (ObjectUtils.isEmpty(idFormat)) {
                throw new CustomException("IDGEN_FORMAT_ERROR", "Blank format is not allowed");
            }
        }

        List<String> matchList = new ArrayList<String>();

        Pattern regExpPattern = Pattern.compile("\\[(.*?)\\]");
        Matcher regExpMatcher = regExpPattern.matcher(idFormat);

        Integer count = getCount(idRequest);

        while (regExpMatcher.find()) {// Finds Matching Pattern in String
            matchList.add(regExpMatcher.group(1));// Fetching Group from String
        }

        HashMap<String, List<String>> sequences = new HashMap<>();
        String idFormatTemplate = idFormat;
        String cityName = null;

        for (int i = 0; i < count; i++) {
            idFormat = idFormatTemplate;

            for (String attributeName : matchList) {

                if (attributeName.substring(0, 3).equalsIgnoreCase("seq")) {
                    if (!sequences.containsKey(attributeName)) {
                        sequences.put(attributeName, generateSequenceNumber(attributeName, requestInfo, idRequest,autoCreateNewSeqFlag));
                    }
					idFormat = idFormat.replace("[" + attributeName + "]", sequences.get(attributeName).get(i));
                } else if (attributeName.substring(0, 2).equalsIgnoreCase("fy")) {
                    idFormat = idFormat.replace("[" + attributeName + "]",
                            generateFinancialYearDateFormat(attributeName, requestInfo));
                } else if (attributeName.substring(0, 2).equalsIgnoreCase("cy")) {
                    idFormat = idFormat.replace("[" + attributeName + "]",
                            generateCurrentYearDateFormat(attributeName, requestInfo));
                } else if (attributeName.substring(0, 4).equalsIgnoreCase("city")) {
                    if (cityName == null) {
                        cityName = mdmsService.getCity(requestInfo, idRequest);
                    }
                    idFormat = idFormat.replace("[" + attributeName + "]", cityName);
                } else {
                    idFormat = idFormat.replace("[" + attributeName + "]", generateRandomText(attributeName, requestInfo));
                }
            }
            idFormatList.add(idFormat);
        }

        return idFormatList;
    }

    /**
     * Description : This method to generate current financial year in given
     * format
     *
     * @param requestInfo
     * @return formattedDate
     */
    private String generateFinancialYearDateFormat(String financialYearFormat, RequestInfo requestInfo) {
        try {

            Date date = new Date();
            financialYearFormat = financialYearFormat.substring(financialYearFormat.indexOf(":") + 1);
            financialYearFormat = financialYearFormat.trim();
            String currentFinancialYear = null;
            String[] financialYearPatternArray;
            financialYearPatternArray = financialYearFormat.split("-");
            int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
            int preYear = 0;
            int postYear = 0;

            for (String yearPattern : financialYearPatternArray) {

                String formattedYear = null;
                SimpleDateFormat formatter = new SimpleDateFormat(yearPattern.trim());
                formattedYear = formatter.format(date);

                if (financialYearPatternArray[0] == yearPattern) {
                    if (month > 3) {
                        preYear = Integer.valueOf(formattedYear);
                    } else {
                        preYear = Integer.valueOf(formattedYear) - 1;
                    }
                } else {
                    if (month > 3) {
                        postYear = Integer.valueOf(formattedYear) + 1;
                    } else {
                        postYear = Integer.valueOf(formattedYear);
                    }
                }
            }
            currentFinancialYear = preYear + "-" + postYear;
            return currentFinancialYear;

        } catch (Exception e) {

            throw new CustomException("INVALID_FORMAT", "Error while generating financial year in provided format. Given format invalid.");
            //throw new InvalidIDFormatException(propertiesManager.getInvalidIdFormat(), requestInfo);

        }
    }

    /**
     * Description : This method to generate current year date in given format
     *
     * @param dateFormat
     * @param requestInfo
     * @return formattedDate
     */
    private String generateCurrentYearDateFormat(String dateFormat, RequestInfo requestInfo) {
        try {

            Date date = new Date();
            dateFormat = dateFormat.trim();
            dateFormat = dateFormat.substring(dateFormat.indexOf(":") + 1);
            dateFormat = dateFormat.trim();
            SimpleDateFormat formatter = new SimpleDateFormat(dateFormat.trim());
            formatter.setTimeZone(TimeZone.getTimeZone(propertiesManager.getTimeZone()));
            String formattedDate = formatter.format(date);
            return formattedDate;

        } catch (Exception e) {

            throw new CustomException("INVALID_FORMAT", "Error while generating current year in provided format. Given format invalid.");
            //throw new InvalidIDFormatException(propertiesManager.getInvalidIdFormat(), requestInfo);

        }
    }

    /**
     * Description : This method to generate random text
     *
     * @param regex
     * @param requestInfo
     * @return randomTxt
     */
    private String generateRandomText(String regex, RequestInfo requestInfo) {
        Random random = new Random();
        List<String> matchList = new ArrayList<String>();
        int length = 2;// default digits length
        try {
            Pattern.compile(regex);
        } catch (Exception e) {
            throw new CustomException("INVALID_REGEX", "Random text could not be generated. Invalid regex provided.");
            //throw new InvalidIDFormatException(propertiesManager.getInvalidIdFormat(), requestInfo);
        }
        Matcher matcher = Pattern.compile("\\{(.*?)\\}").matcher(regex);
        while (matcher.find()) {
            matchList.add(matcher.group(1));
        }
        if (matchList.size() > 0) {
            length = Integer.parseInt(matchList.get(0));
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            stringBuilder.append(random.nextInt(25));
        }
        String randomTxt = stringBuilder.toString();
        randomTxt = randomTxt.substring(0, length);
        return randomTxt;
    }

    /**
     * Description : This method to set default count value
     *
     * @param idRequest
     * @return count
     */
    private Integer getCount(IdRequest idRequest) {
        Integer count;
        if (idRequest.getCount() == null) {
            count = defaultCount;
        } else {
            count = idRequest.getCount();
        }
        return count;
    }

    /**
     * Description : This method to generate sequence in DB
     *
     * @param sequenceName
     */

    private void createSequenceInDb(String sequenceName) throws Exception {

        StringBuilder query = new StringBuilder("CREATE SEQUENCE ");
        try {
            query = query.append(sequenceName);
            jdbcTemplate.execute(query.toString());
        }catch (Exception ex){
            log.error("Error creating new sequence",ex);
        }
    }

    /**
     * Description : This method to generate sequence number
     *
     * @param sequenceName
     * @param requestInfo
     * @return seqNumber
     */
    private List<String> generateSequenceNumber(String sequenceName, RequestInfo requestInfo, IdRequest idRequest,boolean autoCreateNewSeqFlag) throws Exception {
        Integer count = getCount(idRequest);
        List<String> sequenceList = new LinkedList<>();
        List<String> sequenceLists = new LinkedList<>();
        // To generate a block of seq numbers

        String sequenceSql = "SELECT NEXTVAL ('" + sequenceName + "') FROM GENERATE_SERIES(1,?)";
        try {
            sequenceList = jdbcTemplate.queryForList(sequenceSql, new Object[]{count}, String.class);
        } catch (BadSqlGrammarException ex) {
            if (ex.getSQLException().getSQLState().equals("42P01")){
                try{
                    if (sequenceList.isEmpty() && autoCreateNewSeqFlag && autoCreateNewSeq){
                        createSequenceInDb(sequenceName);
                        sequenceList = jdbcTemplate.queryForList(sequenceSql, new Object[]{count}, String.class);
                    }
                    else if(sequenceList.isEmpty() && !autoCreateNewSeqFlag)
                        throw new CustomException("SEQ_DOES_NOT_EXIST","auto creation of seq is not allowed in DB");
                }catch(Exception e) {
                    throw new CustomException("ERROR_CREATING_SEQ","Error occurred while auto creating seq in DB");
                }
            }else{
                throw new CustomException("SEQ_NUMBER_ERROR","Error in retrieving seq number from DB");
            }
        } catch (Exception ex) {
            log.error("Error retrieving seq number from DB",ex);
            throw new CustomException("SEQ_NUMBER_ERROR","Error retrieving seq number from existing seq in DB");
        }
        for (String seqId : sequenceList) {
            String seqNumber = String.format("%0" + paddingLength + "d", Long.parseLong(seqId));
            sequenceLists.add(seqNumber);
        }
        return sequenceLists;
    }

}

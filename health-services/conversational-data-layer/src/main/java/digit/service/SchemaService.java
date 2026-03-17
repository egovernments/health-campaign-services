package digit.service;

import digit.web.models.IndexField;
import digit.web.models.IndexSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SchemaService {

    private final Map<String, IndexSchema> schemaRegistry = new HashMap<>();

    @PostConstruct
    public void initializeSchemas() {
        registerSchema(buildProjectTaskSchema());
        registerSchema(buildProjectSchema());
        registerSchema(buildProjectStaffSchema());
        registerSchema(buildServiceTaskSchema());
        registerSchema(buildStockSchema());
        registerSchema(buildHouseholdSchema());
        registerSchema(buildHouseholdMemberSchema());
        registerSchema(buildReferralSchema());
        registerSchema(buildHfReferralSchema());
        registerSchema(buildHfReferralFeverSchema());
        registerSchema(buildHfReferralDrugReactionSchema());
        registerSchema(buildSideEffectSchema());
        registerSchema(buildStockReconciliationSchema());
        registerSchema(buildAttendanceLogSchema());
        registerSchema(buildAttendanceRegisterSchema());
        registerSchema(buildPgrSchema());
        registerSchema(buildIndividualSchema());
        registerSchema(buildProjectBeneficiarySchema());
        log.info("Initialized {} index schemas: {}", schemaRegistry.size(), schemaRegistry.keySet());
    }

    private void registerSchema(IndexSchema schema) {
        schemaRegistry.put(schema.getIndexName(), schema);
    }

    public IndexSchema getSchema(String indexName) {
        return schemaRegistry.get(indexName);
    }

    public boolean hasSchema(String indexName) {
        return schemaRegistry.containsKey(indexName);
    }

    public Set<String> getAllIndexNames() {
        return Collections.unmodifiableSet(schemaRegistry.keySet());
    }

    /**
     * Returns a compact catalog of all indexes with name and description for index selection.
     */
    public String buildIndexCatalogForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE INDEXES:\n");
        for (IndexSchema schema : schemaRegistry.values()) {
            sb.append("- ").append(schema.getIndexName());
            if (schema.getDescription() != null) {
                sb.append(": ").append(schema.getDescription());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Formats the schema for inclusion in an LLM prompt.
     */
    public String formatSchemaForPrompt(IndexSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("Index: ").append(schema.getIndexName()).append("\n");
        if (schema.getDescription() != null) {
            sb.append("Description: ").append(schema.getDescription()).append("\n");
        }
        sb.append("Fields:\n");
        for (IndexField field : schema.getFields()) {
            sb.append("  ").append(field.getName()).append(" (").append(field.getType()).append(")");
            if (field.getDescription() != null) {
                sb.append(" - ").append(field.getDescription());
            }
            if (field.getEnumValues() != null && !field.getEnumValues().isEmpty()) {
                sb.append(" [").append(String.join(", ", field.getEnumValues())).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ========== Schema builders ==========

    private IndexSchema buildProjectTaskSchema() {
        List<IndexField> fields = new ArrayList<>();

        // Core fields
        fields.add(field("Data.@timestamp", "date", "Record timestamp"));
        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.taskId", "keyword", "Task identifier"));
        fields.add(field("Data.taskClientReferenceId", "keyword", "Client-side task reference ID"));
        fields.add(field("Data.clientReferenceId", "keyword", "Client reference ID"));
        fields.add(field("Data.individualId", "keyword", "Individual/beneficiary identifier"));
        fields.add(field("Data.projectId", "keyword", "Project/campaign identifier"));
        fields.add(field("Data.projectType", "keyword", "Type of project"));
        fields.add(field("Data.projectTypeId", "keyword", "Project type identifier"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));

        // User fields
        fields.add(field("Data.nameOfUser", "text", "Name of the field worker"));
        fields.add(field("Data.userName", "text", "Username of the field worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.gender", "keyword", "Gender of the beneficiary"));
        fields.add(field("Data.age", "long", "Age of the beneficiary in months"));
        fields.add(field("Data.dateOfBirth", "long", "Date of birth (epoch ms)"));

        // Status fields
        fields.add(fieldWithEnum("Data.status", "keyword", "Task delivery status",
                List.of("ADMINISTRATION_FAILED", "ADMINISTRATION_SUCCESS",
                        "BENEFICIARY_REFUSED", "CLOSED_HOUSEHOLD",
                        "DELIVERED", "NOT_ADMINISTERED", "INELIGIBLE")));
        fields.add(field("Data.administrationStatus", "keyword", "Administration status"));
        fields.add(field("Data.taskType", "keyword", "Type of task"));
        fields.add(field("Data.isDelivered", "boolean", "Whether delivery was completed"));
        fields.add(field("Data.deliveredTo", "text", "Who the delivery was made to"));
        fields.add(field("Data.deliveryComments", "text", "Comments on the delivery"));

        // Product fields
        fields.add(field("Data.productName", "keyword", "Name of the product delivered"));
        fields.add(field("Data.productVariant", "keyword", "Variant of the product"));
        fields.add(field("Data.quantity", "long", "Number of units delivered"));

        // Audit fields
        fields.add(field("Data.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.lastModifiedBy", "keyword", "User who last modified the record"));
        fields.add(field("Data.lastModifiedTime", "long", "Last modification timestamp (epoch ms)"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));
        fields.add(field("Data.syncedTime", "long", "Sync timestamp (epoch ms)"));
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));

        // Geo fields
        fields.add(field("Data.latitude", "float", "GPS latitude"));
        fields.add(field("Data.longitude", "float", "GPS longitude"));
        fields.add(field("Data.geoPoint", "geo_point", "Geo coordinates for geo queries"));
        fields.add(field("Data.locationAccuracy", "float", "GPS accuracy in meters"));
        fields.add(field("Data.localityCode", "keyword", "Locality boundary code"));

        // Boundary hierarchy
        addBoundaryFields(fields, "Data");

        // Additional details
        fields.add(field("Data.additionalDetails.name", "text", "Beneficiary name"));
        fields.add(field("Data.additionalDetails.age", "keyword", "Beneficiary age"));
        fields.add(field("Data.additionalDetails.gender", "keyword", "Beneficiary gender"));
        fields.add(field("Data.additionalDetails.uniqueBeneficiaryId", "keyword", "Unique beneficiary ID"));
        fields.add(field("Data.additionalDetails.individualClientReferenceId", "keyword", "Individual client reference ID"));
        fields.add(field("Data.additionalDetails.deliveryStrategy", "keyword", "Delivery strategy used"));
        fields.add(field("Data.additionalDetails.deliveryType", "keyword", "Type of delivery"));
        fields.add(field("Data.additionalDetails.doseIndex", "keyword", "Dose number/index"));
        fields.add(field("Data.additionalDetails.cycleIndex", "keyword", "Campaign cycle index"));
        fields.add(field("Data.additionalDetails.taskStatus", "keyword", "Task status from additional details"));
        fields.add(field("Data.additionalDetails.reAdministered", "keyword", "Whether re-administered"));
        fields.add(field("Data.additionalDetails.ineligibleReasons", "keyword", "Reasons for ineligibility"));
        fields.add(field("Data.additionalDetails.latitude", "keyword", "Latitude from additional details"));
        fields.add(field("Data.additionalDetails.longitude", "keyword", "Longitude from additional details"));
        fields.add(field("Data.additionalDetails.dateOfAdministration", "keyword", "Date product was administered"));
        fields.add(field("Data.additionalDetails.dateOfDelivery", "keyword", "Date of delivery"));
        fields.add(field("Data.additionalDetails.dateOfVerification", "keyword", "Date of verification"));

        return IndexSchema.builder()
                .indexName("oy-project-task-index-v1")
                .description("Denormalized index combining task delivery data with individual/beneficiary details, product info, and boundary hierarchy. Use for deliveries, administration status, beneficiary demographics, task counts by location, and campaign delivery performance.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildProjectSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.projectId", "keyword", "Project/campaign identifier"));
        fields.add(field("Data.projectBeneficiaryType", "keyword", "Type of beneficiary (HOUSEHOLD, INDIVIDUAL)"));
        fields.add(field("Data.overallTarget", "long", "Total target count for the project"));
        fields.add(field("Data.targetPerDay", "long", "Daily target count"));
        fields.add(field("Data.campaignDurationInDays", "long", "Duration of campaign in days"));
        fields.add(field("Data.startDate", "long", "Campaign start date (epoch ms)"));
        fields.add(field("Data.endDate", "long", "Campaign end date (epoch ms)"));
        fields.add(field("Data.productVariant", "keyword", "Product variant assigned"));
        fields.add(field("Data.productName", "keyword", "Name of the product"));
        fields.add(field("Data.targetType", "keyword", "Type of target (household, individual)"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.projectType", "keyword", "Type of project"));
        fields.add(field("Data.projectTypeId", "keyword", "Project type identifier"));
        fields.add(field("Data.subProjectType", "keyword", "Sub-project type"));
        fields.add(field("Data.localityCode", "keyword", "Locality boundary code"));
        fields.add(field("Data.taskDates", "date", "List of task dates"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.createdTime", "long", "Creation timestamp (epoch ms)"));

        return IndexSchema.builder()
                .indexName("oy-project-index-v1")
                .description("Project/campaign configuration data including targets, duration, product variants, and geographic assignment. Use for campaign setup, targets, project duration, and product-location mapping.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildProjectStaffSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.userId", "keyword", "Staff user identifier"));
        fields.add(field("Data.projectId", "keyword", "Project/campaign the staff is assigned to"));
        fields.add(field("Data.userName", "text", "Username of the staff member"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the staff member"));
        fields.add(field("Data.role", "keyword", "Role of the staff member"));
        fields.add(field("Data.userAddress", "text", "Address of the staff member"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.projectType", "keyword", "Type of project"));
        fields.add(field("Data.projectTypeId", "keyword", "Project type identifier"));
        fields.add(field("Data.localityCode", "keyword", "Locality boundary code"));
        fields.add(field("Data.taskDates", "date", "List of task dates"));
        fields.add(field("Data.isDeleted", "boolean", "Soft delete flag"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.createdTime", "long", "Creation timestamp (epoch ms)"));

        return IndexSchema.builder()
                .indexName("oy-project-staff-index-v1")
                .description("Staff assignments to projects and their geographic posting. Use for staff distribution, roles, project assignments, and staff by location.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildServiceTaskSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.supervisorLevel", "keyword", "Level of the supervisor"));
        fields.add(field("Data.checklistName", "keyword", "Name of the checklist filled"));
        fields.add(field("Data.projectId", "keyword", "Project/campaign identifier"));
        fields.add(field("Data.serviceDefinitionId", "keyword", "Service definition identifier"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.userId", "keyword", "User who filled the checklist"));
        fields.add(field("Data.userName", "text", "Username of the supervisor"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the supervisor"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the supervisor"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.attributes", "nested", "Checklist attribute responses (attributeCode, value, referenceId)"));
        fields.add(field("Data.transformedChecklist", "object", "Flattened checklist key-value responses"));
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.syncedTime", "long", "Sync timestamp (epoch ms)"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.geoPoint", "geo_point", "Geo coordinates"));

        return IndexSchema.builder()
                .indexName("oy-service-task-v1")
                .description("Checklist/supervision service records filled by supervisors during field visits. Use for supervision visits, checklist completion, supervisor activity.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildStockSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.facilityId", "keyword", "Facility identifier"));
        fields.add(field("Data.facilityName", "text", "Name of the facility"));
        fields.add(field("Data.facilityType", "keyword", "Type of facility"));
        fields.add(field("Data.facilityLevel", "keyword", "Level of the facility in hierarchy"));
        fields.add(field("Data.facilityTarget", "long", "Target stock for the facility"));
        fields.add(field("Data.transactingFacilityId", "keyword", "Counterpart facility in the transaction"));
        fields.add(field("Data.transactingFacilityName", "text", "Name of the counterpart facility"));
        fields.add(field("Data.transactingFacilityType", "keyword", "Type of the counterpart facility"));
        fields.add(field("Data.transactingFacilityLevel", "keyword", "Level of the counterpart facility"));
        fields.add(field("Data.productVariant", "keyword", "Product variant involved"));
        fields.add(field("Data.productName", "keyword", "Name of the product"));
        fields.add(field("Data.physicalCount", "long", "Physical count of stock"));
        fields.add(fieldWithEnum("Data.eventType", "keyword", "Type of stock transaction",
                List.of("RECEIVED", "DISPATCHED")));
        fields.add(fieldWithEnum("Data.reason", "keyword", "Reason for the transaction",
                List.of("RECEIVED", "RETURNED", "LOST_IN_STORAGE", "LOST_IN_TRANSIT", "DAMAGED_IN_STORAGE", "DAMAGED_IN_TRANSIT")));
        fields.add(field("Data.dateOfEntry", "long", "Date of stock entry (epoch ms)"));
        fields.add(field("Data.waybillNumber", "keyword", "Waybill/shipment tracking number"));
        fields.add(field("Data.userName", "text", "Username of the staff member"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the staff member"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the staff member"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.syncedTime", "long", "Sync timestamp (epoch ms)"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.lastModifiedBy", "keyword", "User who last modified the record"));
        fields.add(field("Data.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.lastModifiedTime", "long", "Last modification timestamp (epoch ms)"));

        return IndexSchema.builder()
                .indexName("oy-stock-index-v1")
                .description("Stock transactions (receipts and dispatches) for health campaign products at facilities and warehouses. Use for stock levels, stock received/dispatched, stock losses, damaged stock, facility-level inventory, waybill tracking.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildHouseholdSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.household.id", "keyword", "Unique household ID"));
        fields.add(field("Data.household.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.household.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.household.memberCount", "long", "Number of members in the household"));
        fields.add(field("Data.household.address", "object", "Household address details"));
        fields.add(field("Data.household.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.household.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.household.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.household.auditDetails.lastModifiedBy", "keyword", "User who last modified"));
        fields.add(field("Data.household.auditDetails.lastModifiedTime", "long", "Last modification timestamp (epoch ms)"));
        fields.add(field("Data.userName", "text", "Username of the field worker"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the field worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the field worker"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.geoPoint", "geo_point", "Geo coordinates"));

        return IndexSchema.builder()
                .indexName("oy-household-index-v1")
                .description("Registered household data with member count, location, and registration details. Use for household registrations, household counts by location, household-level demographics.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildHouseholdMemberSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.householdMember.id", "keyword", "Unique record ID"));
        fields.add(field("Data.householdMember.householdId", "keyword", "Household identifier"));
        fields.add(field("Data.householdMember.householdClientReferenceId", "keyword", "Client-side household reference"));
        fields.add(field("Data.householdMember.individualId", "keyword", "Individual identifier"));
        fields.add(field("Data.householdMember.individualClientReferenceId", "keyword", "Client-side individual reference"));
        fields.add(field("Data.householdMember.isHeadOfHousehold", "boolean", "Whether this member is head of household"));
        fields.add(field("Data.householdMember.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.householdMember.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.dateOfBirth", "long", "Date of birth (epoch ms)"));
        fields.add(field("Data.age", "long", "Age of the member in months"));
        fields.add(field("Data.gender", "keyword", "Gender of the member"));
        fields.add(field("Data.userName", "text", "Username of the field worker"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the field worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the field worker"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.localityCode", "keyword", "Locality boundary code"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.geoPoint", "geo_point", "Geo coordinates"));

        return IndexSchema.builder()
                .indexName("oy-household-member-index-v1")
                .description("Links individuals to households with demographic details (age, gender, DOB). Use for household composition, member demographics, head-of-household status.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildReferralSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.referral.id", "keyword", "Unique referral ID"));
        fields.add(field("Data.referral.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.referral.projectBeneficiaryId", "keyword", "Project beneficiary ID"));
        fields.add(field("Data.referral.projectBeneficiaryClientReferenceId", "keyword", "Client-side project beneficiary reference"));
        fields.add(field("Data.referral.referrerId", "keyword", "ID of the referring health worker"));
        fields.add(field("Data.referral.recipientType", "keyword", "Type of recipient"));
        fields.add(field("Data.referral.recipientId", "keyword", "ID of the recipient"));
        fields.add(field("Data.referral.reasons", "keyword", "Reasons for referral"));
        fields.add(field("Data.referral.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.referral.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.referral.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.referral.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.individualId", "keyword", "Individual identifier"));
        fields.add(field("Data.dateOfBirth", "long", "Date of birth (epoch ms)"));
        fields.add(field("Data.age", "long", "Age in months"));
        fields.add(field("Data.gender", "keyword", "Gender of the beneficiary"));
        fields.add(field("Data.facilityName", "text", "Name of the referring/receiving facility"));
        fields.add(field("Data.userName", "text", "Username of the field worker"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the field worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the field worker"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));

        return IndexSchema.builder()
                .indexName("oy-referral-index-v1")
                .description("Community-level referrals of beneficiaries between health workers and facilities. Use for referral counts, referral reasons, referred beneficiary demographics, referral patterns by location.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildHfReferralSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.hfReferral.id", "keyword", "Unique HF referral ID"));
        fields.add(field("Data.hfReferral.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.hfReferral.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.hfReferral.projectId", "keyword", "Project/campaign identifier"));
        fields.add(field("Data.hfReferral.projectFacilityId", "keyword", "Project facility identifier"));
        fields.add(field("Data.hfReferral.symptom", "keyword", "Reported symptom"));
        fields.add(field("Data.hfReferral.symptomSurveyId", "keyword", "Symptom survey identifier"));
        fields.add(field("Data.hfReferral.beneficiaryId", "keyword", "Beneficiary identifier"));
        fields.add(field("Data.hfReferral.referralCode", "keyword", "Referral code"));
        fields.add(field("Data.hfReferral.nationalLevelId", "keyword", "National-level identifier"));
        fields.add(field("Data.hfReferral.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.hfReferral.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.hfReferral.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.userName", "text", "Username of the health worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the health worker"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));

        return IndexSchema.builder()
                .indexName("oy-hf-referral-index-v1")
                .description("Health facility-level referrals including symptom surveys, referral codes, and national-level IDs. Use for facility referrals, symptom-based referrals, referral code lookups.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildHfReferralFeverSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.supervisorLevel", "keyword", "Level of the supervisor"));
        fields.add(field("Data.checklistName", "keyword", "Name of the checklist"));
        fields.add(field("Data.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.projectId", "keyword", "Project/campaign identifier"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.syncedTime", "long", "Sync timestamp (epoch ms)"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.userName", "text", "Username of the health worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the health worker"));
        fields.add(field("Data.testedForMalaria", "keyword", "Whether tested for malaria"));
        fields.add(field("Data.malariaResult", "keyword", "Malaria test result (positive/negative)"));
        fields.add(field("Data.admittedWithSeriousIllness", "keyword", "Whether admitted with serious illness"));
        fields.add(field("Data.negativeAndAdmittedWithSeriousIllness", "keyword", "Negative result but admitted with serious illness"));
        fields.add(field("Data.treatedWithAntiMalarials", "keyword", "Whether treated with anti-malarials"));
        fields.add(field("Data.nameOfAntiMalarials", "keyword", "Name of anti-malarials used"));
        addBoundaryCodeFields(fields, "Data");

        return IndexSchema.builder()
                .indexName("oy-hf-referral-fever-index")
                .description("Health facility referral service/checklist data with malaria testing results when referred for fever. Use for malaria testing, positive/negative results, anti-malarial treatment, serious illness admissions.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildHfReferralDrugReactionSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.supervisorLevel", "keyword", "Level of the supervisor"));
        fields.add(field("Data.checklistName", "keyword", "Name of the checklist"));
        fields.add(field("Data.ageGroup", "keyword", "Age group of the patient"));
        fields.add(field("Data.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.projectId", "keyword", "Project/campaign identifier"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.syncedTime", "long", "Sync timestamp (epoch ms)"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.userName", "text", "Username of the health worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the health worker"));
        fields.add(field("Data.childrenPresentedUS", "long", "Children presented at US level"));
        fields.add(field("Data.malariaPositiveUS", "long", "Malaria positive at US level"));
        fields.add(field("Data.malariaNegativeUS", "long", "Malaria negative at US level"));
        fields.add(field("Data.childrenPresentedAPE", "long", "Children presented at APE level"));
        fields.add(field("Data.malariaPositiveAPE", "long", "Malaria positive at APE level"));
        fields.add(field("Data.malariaNegativeAPE", "long", "Malaria negative at APE level"));
        addBoundaryCodeFields(fields, "Data");

        return IndexSchema.builder()
                .indexName("oy-hf-referral-drug-reaction-index")
                .description("Referral service task checklists with malaria testing data broken down by service level (US/APE). Use for children tested at US vs APE level, malaria positivity rates by service level.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildSideEffectSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.sideEffect.id", "keyword", "Unique side effect ID"));
        fields.add(field("Data.sideEffect.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.sideEffect.taskId", "keyword", "Associated task ID"));
        fields.add(field("Data.sideEffect.taskClientReferenceId", "keyword", "Client-side task reference"));
        fields.add(field("Data.sideEffect.projectBeneficiaryId", "keyword", "Project beneficiary ID"));
        fields.add(field("Data.sideEffect.projectBeneficiaryClientReferenceId", "keyword", "Client-side project beneficiary reference"));
        fields.add(field("Data.sideEffect.symptoms", "keyword", "List of reported symptoms"));
        fields.add(field("Data.sideEffect.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.sideEffect.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.sideEffect.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.sideEffect.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.individualId", "keyword", "Individual identifier"));
        fields.add(field("Data.dateOfBirth", "long", "Date of birth (epoch ms)"));
        fields.add(field("Data.age", "long", "Age in months"));
        fields.add(field("Data.gender", "keyword", "Gender of the beneficiary"));
        fields.add(field("Data.symptoms", "text", "Reported symptoms (flattened)"));
        fields.add(field("Data.userName", "text", "Username of the field worker"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the field worker"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the field worker"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.localityCode", "keyword", "Locality boundary code"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));

        return IndexSchema.builder()
                .indexName("oy-side-effect-index-v1")
                .description("Adverse side effects reported after product administration, linked to beneficiaries and tasks. Use for side effects, adverse reactions, symptoms, side effect rates by demographics or location.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildStockReconciliationSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.stockReconciliation.id", "keyword", "Unique reconciliation ID"));
        fields.add(field("Data.stockReconciliation.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.stockReconciliation.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.stockReconciliation.facilityId", "keyword", "Facility identifier"));
        fields.add(field("Data.stockReconciliation.productVariantId", "keyword", "Product variant identifier"));
        fields.add(field("Data.stockReconciliation.referenceId", "keyword", "Reference identifier"));
        fields.add(field("Data.stockReconciliation.referenceIdType", "keyword", "Type of reference ID"));
        fields.add(field("Data.stockReconciliation.physicalCount", "long", "Actual physical count of stock"));
        fields.add(field("Data.stockReconciliation.calculatedCount", "long", "System-calculated expected count"));
        fields.add(field("Data.stockReconciliation.commentsOnReconciliation", "text", "Comments on the reconciliation"));
        fields.add(field("Data.stockReconciliation.dateOfReconciliation", "long", "Date of reconciliation (epoch ms)"));
        fields.add(field("Data.stockReconciliation.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.stockReconciliation.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.stockReconciliation.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.facilityName", "text", "Name of the facility"));
        fields.add(field("Data.facilityType", "keyword", "Type of facility"));
        fields.add(field("Data.facilityLevel", "keyword", "Level of the facility in hierarchy"));
        fields.add(field("Data.facilityTarget", "long", "Target stock for the facility"));
        fields.add(field("Data.productName", "keyword", "Name of the product"));
        fields.add(field("Data.userName", "text", "Username of the staff member"));
        fields.add(field("Data.nameOfUser", "text", "Full name of the staff member"));
        fields.add(field("Data.role", "keyword", "Role of the user"));
        fields.add(field("Data.userAddress", "text", "Address of the staff member"));
        addBoundaryFields(fields, "Data");
        fields.add(field("Data.localityCode", "keyword", "Locality boundary code"));
        fields.add(field("Data.syncedTimeStamp", "date", "Sync timestamp (date format)"));
        fields.add(field("Data.syncedTime", "long", "Sync timestamp (epoch ms)"));
        fields.add(field("Data.syncedDate", "date", "Date the record was synced"));
        fields.add(field("Data.taskDates", "date", "Date of the task"));

        return IndexSchema.builder()
                .indexName("oy-stock-reconciliation-index-v1")
                .description("Stock reconciliation events comparing physical counts vs calculated counts at facilities. Use for stock discrepancies, reconciliation history, physical vs expected counts, facility-level stock accuracy.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildAttendanceLogSchema() {
        List<IndexField> fields = new ArrayList<>();

        // Attendance log indexes do NOT use Data wrapper
        fields.add(field("attendanceLog.id", "keyword", "Unique log ID"));
        fields.add(field("attendanceLog.registerId", "keyword", "Attendance register ID"));
        fields.add(field("attendanceLog.individualId", "keyword", "Individual (attendee) ID"));
        fields.add(field("attendanceLog.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("attendanceLog.time", "long", "Attendance timestamp"));
        fields.add(fieldWithEnum("attendanceLog.type", "keyword", "Attendance type", List.of("ENTRY", "EXIT")));
        fields.add(field("attendanceLog.status", "keyword", "Attendance status"));
        fields.add(field("attendanceLog.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("attendanceLog.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("givenName", "text", "Given name of the attendee"));
        fields.add(field("familyName", "text", "Family name of the attendee"));
        fields.add(field("attendanceTime", "date", "Formatted attendance time"));
        fields.add(field("registerServiceCode", "keyword", "Service code of the register"));
        fields.add(field("registerName", "text", "Name of the attendance register"));
        fields.add(field("registerNumber", "keyword", "Register number"));
        fields.add(field("userName", "text", "Username of the field worker"));
        fields.add(field("nameOfUser", "text", "Full name of the field worker"));
        fields.add(field("role", "keyword", "Role of the user"));
        fields.add(field("userAddress", "text", "Address of the field worker"));
        addBoundaryFields(fields, null);

        return IndexSchema.builder()
                .indexName("oy-transformer-save-attendance-log")
                .description("Individual attendance log entries for health campaign workers linked to attendance registers. Use for worker attendance, attendance times, register-level tracking.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildAttendanceRegisterSchema() {
        List<IndexField> fields = new ArrayList<>();

        // Attendance register indexes do NOT use Data wrapper
        fields.add(field("attendanceRegister.id", "keyword", "Unique register ID"));
        fields.add(field("attendanceRegister.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("attendanceRegister.registerNumber", "keyword", "Register number"));
        fields.add(field("attendanceRegister.name", "text", "Register name"));
        fields.add(field("attendanceRegister.referenceId", "keyword", "Reference identifier"));
        fields.add(field("attendanceRegister.serviceCode", "keyword", "Service code"));
        fields.add(field("attendanceRegister.startDate", "long", "Register start date (epoch ms)"));
        fields.add(field("attendanceRegister.endDate", "long", "Register end date (epoch ms)"));
        fields.add(field("attendanceRegister.status", "keyword", "Register status"));
        fields.add(field("attendanceRegister.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("attendanceRegister.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("attendeesInfo", "object", "Map of attendee IDs to names"));
        fields.add(field("transformerTimeStamp", "date", "Transformer processing timestamp"));

        return IndexSchema.builder()
                .indexName("oy-transformer-save-attendance-register")
                .description("Attendance register metadata with attendee lists and staff info. Use for attendance registers, register periods, registered attendees, register-level summaries.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildPgrSchema() {
        List<IndexField> fields = new ArrayList<>();

        // PGR index does NOT use Data wrapper
        fields.add(field("service.id", "keyword", "Unique service request ID"));
        fields.add(field("service.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("service.serviceCode", "keyword", "Service/complaint code"));
        fields.add(field("service.serviceRequestId", "keyword", "Service request identifier"));
        fields.add(field("service.description", "text", "Description of the complaint"));
        fields.add(field("service.accountId", "keyword", "Account identifier"));
        fields.add(field("service.rating", "long", "Rating given"));
        fields.add(field("service.applicationStatus", "keyword", "Status of the complaint"));
        fields.add(field("service.source", "keyword", "Source of the complaint"));
        fields.add(field("service.active", "boolean", "Whether the complaint is active"));
        fields.add(field("service.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("service.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("userName", "text", "Username of the handler"));
        fields.add(field("nameOfUser", "text", "Full name of the handler"));
        fields.add(field("role", "keyword", "Role of the user"));
        fields.add(field("userAddress", "text", "Address of the handler"));
        addBoundaryFields(fields, null);
        fields.add(field("localityCode", "keyword", "Locality boundary code"));
        fields.add(field("taskDates", "date", "Date of the task"));

        return IndexSchema.builder()
                .indexName("oy-pgr-services")
                .description("Public grievance/complaint records including service requests, status, and resolution details. Use for complaints, grievances, service request status, complaint patterns by location.")
                .fields(fields)
                .build();
    }

    // ========== Helper methods ==========

    /**
     * Adds both boundary hierarchy name and code fields with an optional Data prefix.
     */
    private void addBoundaryFields(List<IndexField> fields, String prefix) {
        String p = (prefix != null) ? prefix + "." : "";
        fields.add(field(p + "boundaryHierarchy.country", "keyword", "Country name"));
        fields.add(field(p + "boundaryHierarchy.state", "keyword", "State name"));
        fields.add(field(p + "boundaryHierarchy.lga", "keyword", "LGA name"));
        fields.add(field(p + "boundaryHierarchy.ward", "keyword", "Ward name"));
        fields.add(field(p + "boundaryHierarchy.community", "keyword", "Community name"));
        fields.add(field(p + "boundaryHierarchy.healthFacility", "keyword", "Health facility name"));
        addBoundaryCodeFields(fields, prefix);
    }

    /**
     * Adds boundary hierarchy code fields with an optional Data prefix.
     */
    private void addBoundaryCodeFields(List<IndexField> fields, String prefix) {
        String p = (prefix != null) ? prefix + "." : "";
        fields.add(field(p + "boundaryHierarchyCode.country", "keyword", "Country code"));
        fields.add(field(p + "boundaryHierarchyCode.state", "keyword", "State code"));
        fields.add(field(p + "boundaryHierarchyCode.lga", "keyword", "LGA code"));
        fields.add(field(p + "boundaryHierarchyCode.ward", "keyword", "Ward code"));
        fields.add(field(p + "boundaryHierarchyCode.community", "keyword", "Community code"));
        fields.add(field(p + "boundaryHierarchyCode.healthFacility", "keyword", "Health facility code"));
    }

    private IndexSchema buildIndividualSchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique individual record ID"));
        fields.add(field("Data.individualId", "keyword", "Individual identifier"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.userId", "keyword", "Associated user ID"));
        fields.add(field("Data.userUuid", "keyword", "Associated user UUID"));
        fields.add(field("Data.name.givenName", "text", "First/given name"));
        fields.add(field("Data.name.familyName", "text", "Family/last name"));
        fields.add(field("Data.name.otherNames", "text", "Other/middle names"));
        fields.add(field("Data.dateOfBirth", "date", "Date of birth (dd/MM/yyyy)"));
        fields.add(fieldWithEnum("Data.gender", "keyword", "Gender of the individual",
                List.of("MALE", "FEMALE", "OTHER", "TRANSGENDER")));
        fields.add(fieldWithEnum("Data.bloodGroup", "keyword", "Blood group",
                List.of("B+", "B-", "A+", "A-", "AB+", "AB-", "O-", "O+")));
        fields.add(field("Data.mobileNumber", "keyword", "Mobile phone number"));
        fields.add(field("Data.altContactNumber", "keyword", "Alternate contact number"));
        fields.add(field("Data.email", "keyword", "Email address"));
        fields.add(field("Data.fatherName", "text", "Father's name"));
        fields.add(field("Data.husbandName", "text", "Husband's name"));
        fields.add(field("Data.relationship", "keyword", "Relationship type"));
        fields.add(field("Data.photo", "keyword", "Photo reference"));
        fields.add(field("Data.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.isSystemUser", "boolean", "Whether individual is a system user; if true then it is not a beneficiary"));
        fields.add(field("Data.isSystemUserActive", "boolean", "Whether system user account is active"));
        fields.add(field("Data.address", "nested", "List of addresses (max 3)"));
        fields.add(field("Data.identifiers", "nested", "Identity documents (type + ID)"));
        fields.add(field("Data.skills", "nested", "Skills (type, level, experience)"));
        fields.add(field("Data.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.auditDetails.lastModifiedBy", "keyword", "User who last modified"));
        fields.add(field("Data.auditDetails.lastModifiedTime", "long", "Last modification timestamp (epoch ms)"));

        return IndexSchema.builder()
                .indexName("oy-individual-index-v1")
                .description("Individual/person records with demographic details including name, date of birth, gender, contact information, identifiers, and skills. Use for individual lookups, demographic queries, contact searches, identifier lookups.")
                .fields(fields)
                .build();
    }

    private IndexSchema buildProjectBeneficiarySchema() {
        List<IndexField> fields = new ArrayList<>();

        fields.add(field("Data.id", "keyword", "Unique record ID"));
        fields.add(field("Data.projectId", "keyword", "Project/campaign identifier"));
        fields.add(field("Data.beneficiaryId", "keyword", "Beneficiary entity ID"));
        fields.add(field("Data.clientReferenceId", "keyword", "Client-side reference ID"));
        fields.add(field("Data.beneficiaryClientReferenceId", "keyword", "Client-side beneficiary reference ID"));
        fields.add(field("Data.dateOfRegistration", "long", "Registration date (epoch ms)"));
        fields.add(field("Data.tenantId", "keyword", "Tenant identifier"));
        fields.add(field("Data.tag", "keyword", "Beneficiary tag/label"));
        fields.add(field("Data.isDeleted", "boolean", "Soft delete flag"));
        fields.add(field("Data.auditDetails.createdBy", "keyword", "User who created the record"));
        fields.add(field("Data.auditDetails.createdTime", "long", "Creation timestamp (epoch ms)"));
        fields.add(field("Data.auditDetails.lastModifiedBy", "keyword", "User who last modified"));
        fields.add(field("Data.auditDetails.lastModifiedTime", "long", "Last modification timestamp (epoch ms)"));

        return IndexSchema.builder()
                .indexName("oy-project-beneficiary-index-v1")
                .description("Registration of beneficiaries (individuals/households) to projects/campaigns. Links beneficiary entities to specific projects with registration dates. Use for beneficiary enrollment, registration counts, project-beneficiary lookups, registration date queries.")
                .fields(fields)
                .build();
    }

    private IndexField field(String name, String type, String description) {
        return IndexField.builder().name(name).type(type).description(description).build();
    }

    private IndexField fieldWithEnum(String name, String type, String description, List<String> enumValues) {
        return IndexField.builder().name(name).type(type).description(description).enumValues(enumValues).build();
    }
}

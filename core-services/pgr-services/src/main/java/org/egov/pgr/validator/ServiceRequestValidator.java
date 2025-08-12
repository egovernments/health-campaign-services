package org.egov.pgr.validator;

import com.jayway.jsonpath.JsonPath;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.repository.PGRRepository;
import org.egov.pgr.util.HRMSUtil;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.egov.pgr.web.models.Service;
import org.egov.pgr.web.models.ServiceRequest;
import org.egov.pgr.web.models.ServiceWrapper;
import org.egov.pgr.web.models.User;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.pgr.util.PGRConstants.INVALID_TENANT_ID_ERR_CODE;
import static org.egov.pgr.util.PGRConstants.MDMS_DEPARTMENT_SEARCH;
import static org.egov.pgr.util.PGRConstants.MDMS_SERVICEDEF_SEARCH;
import static org.egov.pgr.util.PGRConstants.PGR_WF_REOPEN;
import static org.egov.pgr.util.PGRConstants.USERTYPE_CITIZEN;
import static org.egov.pgr.util.PGRConstants.USERTYPE_EMPLOYEE;

@Component
public class ServiceRequestValidator {


    private PGRConfiguration config;

    private PGRRepository repository;

    private HRMSUtil hrmsUtil;

    @Value("${enable.state.level.search:true}")
    private Boolean enableStateLevelSearch;

    @Value("${enable.usertype.employee:true}")
    private Boolean enableEmployee;

    @Autowired
    public ServiceRequestValidator(PGRConfiguration config, PGRRepository repository, HRMSUtil hrmsUtil) {
        this.config = config;
        this.repository = repository;
        this.hrmsUtil = hrmsUtil;
    }


    /**
     * Validates the create request
     * @param request Request for creating the complaint
     * @param mdmsData The master data for pgr
     */
    public void validateCreate(ServiceRequest request, Object mdmsData){
        Map<String,String> errorMap = new HashMap<>();
        validateUserData(request,errorMap);
        validateSource(request.getService().getSource());
        validateMDMS(request, mdmsData);
        validateDepartment(request, mdmsData);
        if(!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }


    /**
     * Validates if the update request is valid
     * @param request The request to update complaint
     * @param mdmsData The master data for pgr
     */
    public void validateUpdate(ServiceRequest request, Object mdmsData) {

        String id = request.getService().getId();
        String tenantId = request.getService().getTenantId();
        validateSource(request.getService().getSource());
        validateMDMS(request, mdmsData);
        validateDepartment(request, mdmsData);
        validateReOpen(request);
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().ids(Collections.singleton(id)).build();
        criteria.setIsPlainSearch(false);
        criteria.setTenantId(tenantId);
        List<ServiceWrapper> serviceWrappers = null;
        try {
            serviceWrappers = repository.getServiceWrappers(criteria);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }

        if(CollectionUtils.isEmpty(serviceWrappers))
            throw new CustomException("INVALID_UPDATE","The record that you are trying to update does not exists");

        // TO DO

    }

    /**
     * Validates the user related data in the complaint
     * @param request The request of creating/updating complaint
     * @param errorMap HashMap to capture any errors
     */
    private void validateUserData(ServiceRequest request,Map<String, String> errorMap){

        RequestInfo requestInfo = request.getRequestInfo();
        String accountId = request.getService().getAccountId();

        /*if(requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_CITIZEN)
            && StringUtils.isEmpty(accountId)){
            errorMap.put("INVALID_REQUEST","AccountId cannot be null");
        }
        else if(requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_CITIZEN)
                && !StringUtils.isEmpty(accountId)
                && !accountId.equalsIgnoreCase(requestInfo.getUserInfo().getUuid())){
            errorMap.put("INVALID_ACCOUNTID","The accountId is different from the user logged in");
        }*/

        if(requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_CITIZEN)
        || Boolean.TRUE.equals(enableEmployee && requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_EMPLOYEE))){
            User user = request.getService().getUser();

            if(user == null)
                errorMap.put("INVALID_REQUEST","User object cannot be null");
            else if(user.getMobileNumber()==null || user.getName()==null)
                errorMap.put("INVALID_REQUEST","Name and Mobile Number is mandatory in user object");
        }
    }


    /**
     * Validated the master data sent in the request
     * @param request The request of creating/updating complaint
     * @param mdmsData The master data for pgr
     */
    private void validateMDMS(ServiceRequest request, Object mdmsData){

        String serviceCode = request.getService().getServiceCode();
        String jsonPath = MDMS_SERVICEDEF_SEARCH.replace("{SERVICEDEF}",serviceCode);

        List<Object> res = null;

        try{
            res = JsonPath.read(mdmsData,jsonPath);
        }
        catch (Exception e){
            throw new CustomException("JSONPATH_ERROR","Failed to parse mdms response");
        }

        if(CollectionUtils.isEmpty(res))
            throw new CustomException("INVALID_SERVICECODE","The service code: "+serviceCode+" is not present in MDMS");


    }


    /**
     *
     * @param request
     * @param mdmsData
     */
    private void validateDepartment(ServiceRequest request, Object mdmsData){

        String serviceCode = request.getService().getServiceCode();
        String tenantId = request.getService().getTenantId();
        List<String> assignes = request.getWorkflow().getAssignes();

        List<String> hrmsAssignes = request.getWorkflow().getHrmsAssignees();

        if(CollectionUtils.isEmpty(assignes))
            return;

        List<String> departments = hrmsUtil.getDepartment(tenantId, assignes, hrmsAssignes, request.getRequestInfo());

        String jsonPath = MDMS_DEPARTMENT_SEARCH.replace("{SERVICEDEF}",serviceCode);

        List<String> res = null;
        String departmentFromMDMS;

        try{
            res = JsonPath.read(mdmsData,jsonPath);
        }
        catch (Exception e){
            throw new CustomException("JSONPATH_ERROR","Failed to parse mdms response for department");
        }

        if(CollectionUtils.isEmpty(res))
            throw new CustomException("PARSING_ERROR","Failed to fetch department from mdms data for serviceCode: "+serviceCode);
        else departmentFromMDMS = res.get(0);

        Map<String, String> errorMap = new HashMap<>();

        if(!departments.contains(departmentFromMDMS))
            errorMap.put("INVALID_ASSIGNMENT","The application cannot be assigned to employee of department: "+departments.toString());


        if(!errorMap.isEmpty())
            throw new CustomException(errorMap);

    }


    /**
     *
     * @param request
     */
    private void validateReOpen(ServiceRequest request){

        if(!request.getWorkflow().getAction().equalsIgnoreCase(PGR_WF_REOPEN))
            return;


        Service service = request.getService();
        RequestInfo requestInfo = request.getRequestInfo();
        Long lastModifiedTime = service.getAuditDetails().getLastModifiedTime();

        if(requestInfo.getUserInfo().getType().equalsIgnoreCase(USERTYPE_CITIZEN)){
            if(!requestInfo.getUserInfo().getUuid().equalsIgnoreCase(service.getAccountId()))
                throw new CustomException("INVALID_ACTION","Not authorized to re-open the complain");
        }

        if(System.currentTimeMillis()-lastModifiedTime > config.getComplainMaxIdleTime())
            throw new CustomException("INVALID_ACTION","Complaint is closed");

    }


    /**
     *
     * @param criteria
     */
    public void validateSearch(RequestInfo requestInfo, RequestSearchCriteria criteria){

        /*
        * Checks if tenatId is provided with the search params
        * */
        if( (criteria.getMobileNumber()!=null 
                || criteria.getServiceRequestId()!=null || criteria.getIds()!=null
                || criteria.getServiceCode()!=null )
                && criteria.getTenantId()==null)
            throw new CustomException("INVALID_SEARCH","TenantId is mandatory search param");

        validateSearchParam(requestInfo, criteria);

    }


    /**
     * Validates if the user have access to search on given param
     * @param requestInfo
     * @param criteria
     */
    private void validateSearchParam(RequestInfo requestInfo, RequestSearchCriteria criteria){

        if(requestInfo.getUserInfo().getType().equalsIgnoreCase("EMPLOYEE" ) && criteria.isEmpty())
            throw new CustomException("INVALID_SEARCH","Search without params is not allowed");

        if(requestInfo.getUserInfo().getType().equalsIgnoreCase("EMPLOYEE")
                && criteria.getTenantId().split("\\.").length == 1
        && Boolean.TRUE.equals(!enableStateLevelSearch)){
            throw new CustomException("INVALID_SEARCH", "Employees cannot perform state level searches.");
        }

        String allowedParamStr = null;

        if(requestInfo.getUserInfo().getType().equalsIgnoreCase("CITIZEN" ))
            allowedParamStr = config.getAllowedCitizenSearchParameters();
        else if(requestInfo.getUserInfo().getType().equalsIgnoreCase("EMPLOYEE" ) || requestInfo.getUserInfo().getType().equalsIgnoreCase("SYSTEM") )
            allowedParamStr = config.getAllowedEmployeeSearchParameters();
        else throw new CustomException("INVALID SEARCH","The userType: "+requestInfo.getUserInfo().getType()+
                    " does not have any search config");

        List<String> allowedParams = Arrays.asList(allowedParamStr.split(","));

        if(criteria.getServiceCode()!=null && !allowedParams.contains("serviceCode"))
            throw new CustomException("INVALID SEARCH","Search on serviceCode is not allowed");

        if(criteria.getServiceRequestId()!=null && !allowedParams.contains("serviceRequestId"))
            throw new CustomException("INVALID SEARCH","Search on serviceRequestId is not allowed");

        if(criteria.getApplicationStatus()!=null && !allowedParams.contains("applicationStatus"))
            throw new CustomException("INVALID SEARCH","Search on applicationStatus is not allowed");

        if(criteria.getMobileNumber()!=null && !allowedParams.contains("mobileNumber"))
            throw new CustomException("INVALID SEARCH","Search on mobileNumber is not allowed");

        if(criteria.getIds()!=null && !allowedParams.contains("ids"))
            throw new CustomException("INVALID SEARCH","Search on ids is not allowed");

    }

    /**
     * Validates if the source is in the given list configures in application properties
     * @param source
     */
    private void validateSource(String source){

        List<String> allowedSourceStr = Arrays.asList(config.getAllowedSource().split(","));

        if(!allowedSourceStr.contains(source))
            throw new CustomException("INVALID_SOURCE","The source: "+source+" is not valid");

    }


    public void validatePlainSearch(RequestSearchCriteria criteria) {
        if(CollectionUtils.isEmpty(criteria.getTenantIds())){
            throw new CustomException("TENANT_ID_LIST_EMPTY", "Tenant ids not provided for searching.");
        }
    }
}

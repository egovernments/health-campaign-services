package org.egov.project.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class ProjectConstants {
    public static final String MASTER_TENANTS = "tenants";
    public static final String MDMS_TENANT_MODULE_NAME = "tenant";
    public static final String MDMS_COMMON_MASTERS_MODULE_NAME = "common-masters";
    public static final String MDMS_HCM_ATTENDANCE_MODULE_NAME = "HCM-ATTENDANCE";
    public static final String MASTER_DEPARTMENT = "Department";
    public static final String MASTER_PROJECTTYPE = "ProjectType";
    //location
    public static final String MASTER_NATUREOFWORK = "NatureOfWork";
    public static final String MASTER_ATTENDANCE_SESSION = "AttendanceSessions";
    public static final String CODE = "code";
    //General
    public static final String SEMICOLON = ":";
    public static final String DOT = ".";
    public static final String PROJECT_PARENT_HIERARCHY_SEPERATOR = ".";
    public static final String TASK_NOT_ALLOWED = "TASK_NOT_ALLOWED";
    public static final String TASK_NOT_ALLOWED_BENEFICIARY_REFUSED_RESOURCE_EMPTY_ERROR_MESSAGE = "Task not allowed as resources can not be provided when " + TaskStatus.BENEFICIARY_REFUSED;
    public static final String TASK_NOT_ALLOWED_RESOURCE_CANNOT_EMPTY_ERROR_MESSAGE = "Task not allowed as resources can not be empty when ";
    public static final String NUMBER_OF_SESSIONS = "numberOfSessions";

    public enum TaskStatus {
        BENEFICIARY_REFUSED("BENEFICIARY_REFUSED");
        private String value;

        TaskStatus(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static TaskStatus fromValue(String text) {
            for (TaskStatus status : TaskStatus.values()) {
                if (String.valueOf(status.value).equals(text)) {
                    return status;
                }
            }
            return null;
        }
    }

}
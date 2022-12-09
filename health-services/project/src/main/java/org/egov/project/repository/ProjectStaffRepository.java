package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.project.web.models.ProjectStaff;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class ProjectStaffRepository {

    public static final String SAVE_KAFKA_TOPIC = "save-project-staff-topic";
    private final Producer producer;

    @Autowired
    public ProjectStaffRepository(Producer producer) {
        this.producer = producer;
    }

    public ProjectStaff save(ProjectStaff projectStaff) {
        if (projectStaff == null) {
            return null;
        }
        try {
            producer.push(SAVE_KAFKA_TOPIC, projectStaff);
        } catch (Exception exception) {
            log.error("Error during save", exception);
            throw new CustomException(RepositoryErrorCode.SAVE_ERROR.name(),
                    RepositoryErrorCode.SAVE_ERROR.message(exception.getMessage()));
        }
        return projectStaff;
    }
}


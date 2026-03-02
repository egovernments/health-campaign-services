package org.egov.healthnotification.service;

import org.egov.common.models.project.Project;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Test
    public void testSearchProjectById() {
        // Test with the project ID you provided
        String projectId = "00007217-81c4-4428-9ac5-b309accf22ed";
        String tenantId = "dev";

        System.out.println("Testing project search for projectId: " + projectId);

        try {
            Project project = projectService.searchProjectById(projectId, tenantId);

            if (project != null) {
                System.out.println("✅ Project found successfully!");
                System.out.println("Project ID: " + project.getId());
                System.out.println("Project Type: " + project.getProjectType());
                System.out.println("Project Number: " + project.getProjectNumber());
                System.out.println("Department: " + project.getDepartment());
                System.out.println("Tenant ID: " + project.getTenantId());

                assertNotNull(project.getId());
                assertNotNull(project.getProjectType());
                assertEquals("MR-DN", project.getProjectType());
            } else {
                System.out.println("❌ Project not found");
                fail("Project should not be null");
            }
        } catch (Exception e) {
            System.out.println("❌ Error occurred: " + e.getMessage());
            e.printStackTrace();
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }
}

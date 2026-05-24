package com.tarento.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.egov.tracer.config.TracerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Import({TracerConfiguration.class})
@Component("configurationLoader")
public class ConfigurationLoader {

    private static Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);
    private Map<String, ObjectNode> nameContentMap = new HashMap<>();
    // tenantId -> (filename -> ObjectNode) used when is.central.instance=true
    private Map<String, Map<String, ObjectNode>> tenantConfigMap = new HashMap<>();

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    @Autowired
    private ResourceLoader resourceLoader;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${config.schema.paths}")
    private String RESOURCE_LOCATION;

    @Value("${is.central.instance:false}")
    private boolean isCentralInstance;

    @Value("${central.instance.tenants:}")
    private String centralInstanceTenants;

    @Value("${central.instance.base.path:}")
    private String centralInstanceBasePath;

    public static final String ROLE_DASHBOARD_CONFIG = "RoleDashboardMappingsConf.json";
    public static final String MASTER_DASHBOARD_CONFIG = "MasterDashboardConfig.json";

    public static void setCurrentTenant(String tenantId) {
        currentTenant.set(tenantId);
    }

    public static void clearCurrentTenant() {
        currentTenant.remove();
    }

    @PostConstruct
	public void loadResources() throws Exception {
        if (isCentralInstance && centralInstanceTenants != null && !centralInstanceTenants.trim().isEmpty()) {
            for (String tenant : centralInstanceTenants.split(",")) {
                tenant = tenant.trim();
                if (tenant.isEmpty()) continue;
                String tenantPath = centralInstanceBasePath + "/" + tenant + "/dashboard-analytics/*.json";
                Map<String, ObjectNode> tenantMap = new HashMap<>();
                try {
                    Resource[] resources = getResources(tenantPath);
                    for (Resource resource : resources) {
                        String jsonContent = getContent(resource);
                        ObjectNode jsonNode = (ObjectNode) objectMapper.readTree(jsonContent);
                        tenantMap.put(resource.getFilename(), jsonNode);
                    }
                    tenantConfigMap.put(tenant, tenantMap);
                    logger.info("Loaded {} resources for tenant: {}", tenantMap.size(), tenant);
                } catch (Exception e) {
                    logger.error("Failed to load resources for tenant: {}", tenant, e);
                }
            }
        } else {
            Resource[] resources = getResources(RESOURCE_LOCATION);
            for (Resource resource : resources) {
                String jsonContent = getContent(resource);
                ObjectNode jsonNode = (ObjectNode) objectMapper.readTree(jsonContent);
                nameContentMap.put(resource.getFilename(), jsonNode);
            }
            logger.info("Number of resources loaded " + nameContentMap.size());
        }
	}

    public ObjectNode get(String name) {
        if (isCentralInstance) {
            String tenantId = currentTenant.get();
            if (tenantId != null) {
                Map<String, ObjectNode> tenantMap = tenantConfigMap.get(tenantId);
                if (tenantMap != null) {
                    return tenantMap.get(name);
                }
                logger.warn("No config loaded for tenantId: {}", tenantId);
                return null;
            }
            // No tenant on thread (e.g. startup @PostConstruct calls) — fall through to any-tenant lookup
            return getFromAnyTenant(name);
        }
        return nameContentMap.get(name);
    }

    /**
     * For use at startup (no request-scoped tenant available): searches all loaded tenant configs
     * and returns the first match found, or null if not present in any tenant.
     */
    public ObjectNode getFromAnyTenant(String name) {
        for (Map.Entry<String, Map<String, ObjectNode>> entry : tenantConfigMap.entrySet()) {
            ObjectNode node = entry.getValue().get(name);
            if (node != null) {
                logger.debug("getFromAnyTenant: resolved '{}' from tenant '{}'", name, entry.getKey());
                return node;
            }
        }
        logger.warn("getFromAnyTenant: '{}' not found in any tenant config", name);
        return null;
    }

    /**
     * Loads all the resources/files with a given pattern *.json
     * @param pattern   path with *json
     * @return
     * @throws IOException
     */
    private Resource[] getResources(String pattern) throws IOException {
        Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(pattern);
        return resources;
    }

    /**
     * Returns a content of resource
     * 
     * @param resource
     * @return
     */
    private String getContent(Resource resource) {
        String content = null;
        InputStream is = null;
        try {
            is = resource.getInputStream();
            byte[] encoded = IOUtils.toByteArray(is);
            content = new String(encoded, Charset.forName("UTF-8"));

        } catch (IOException e) {
            logger.error("Cannot load resource " + resource.getFilename());

        } finally{
            try {
                if(!ObjectUtils.isEmpty(is))
                    is.close();
            }catch(IOException e){
                logger.error("Error while closing input stream.");
            }
        }
        return content;
    }

}

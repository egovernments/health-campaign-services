package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BoundaryUtil {

	private ObjectMapper mapper;
	private Configuration config;
	private ServiceRequestRepository serviceRequestRepository;

	public BoundaryUtil(Configuration config, ServiceRequestRepository serviceRequestRepository, ObjectMapper mapper) {
		this.config = config;
		this.mapper = mapper;
		this.serviceRequestRepository = serviceRequestRepository;
	}


}

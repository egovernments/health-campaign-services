package org.egov.project.util;

import javax.validation.Valid;

import org.egov.project.web.models.DataErrorDumpRequest;
import org.egov.tracer.kafka.ErrorQueueProducer;
import org.egov.tracer.model.ErrorQueueContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ErrorDumpUtil {

	@Autowired
	private ErrorQueueProducer errorQueueProducer;
	
	public void sendErrorsToQueue(@Valid DataErrorDumpRequest request) {

		for (ErrorQueueContract errorContract : request.getErrorEntities()){
			errorQueueProducer.sendMessage(errorContract);
		}
	}

	
}

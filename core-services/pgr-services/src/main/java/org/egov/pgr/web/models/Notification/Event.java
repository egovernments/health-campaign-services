package org.egov.pgr.web.models.Notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.egov.pgr.web.models.Status;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Validated
@AllArgsConstructor
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
public class Event {
	
	@NotNull
	private String tenantId;
	
	private String id;
	
	private String referenceId;
	
	@NotNull
	private String eventType;
	
	private String name;
	
	@NotNull
	private String description;
	
	private Status status;
	
	@NotNull
	private Source source;
	
	private String postedBy;
	
	@Valid
	@NotNull
	private Recepient recepient;
	
	private Action actions;
	
	private EventDetails eventDetails;
		

}

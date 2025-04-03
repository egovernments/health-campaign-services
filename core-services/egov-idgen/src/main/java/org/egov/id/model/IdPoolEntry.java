package org.egov.id.model;

import lombok.*;

@Getter
@ToString
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IdPoolEntry {

        private String id;

        private String tenantId;
}

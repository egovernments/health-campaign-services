package org.egov.id.model;

import lombok.*;

import java.security.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class IdRecord {

    private String id;

    private String status;

    private Timestamp createdAt;


    public void setCreatedAt(java.sql.Timestamp createdAt) {
    }
}

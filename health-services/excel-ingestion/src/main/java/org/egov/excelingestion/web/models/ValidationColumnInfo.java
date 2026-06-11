package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidationColumnInfo {
    private int statusColumnIndex;
    private int errorColumnIndex;
}
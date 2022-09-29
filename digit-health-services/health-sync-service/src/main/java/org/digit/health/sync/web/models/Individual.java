package org.digit.health.sync.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Individual {
    private String name;

    private String dateOfBirth;

    private String gender;

    private boolean isHead;

    private List<Identifier> indentifiers;
}

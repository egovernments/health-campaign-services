package org.egov.common.helpers;

import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class SomeValidator implements Validator<SomeObject, OtherObject> {
    @Override
    public Map<OtherObject, List<Error>> validate(SomeObject someObject) {
        return Collections.emptyMap();
    }
}

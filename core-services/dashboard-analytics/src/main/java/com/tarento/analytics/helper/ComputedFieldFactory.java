package com.tarento.analytics.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ComputedFieldFactory {

    @Autowired
    private PercentageComputedField percentageComputedField;
    @Autowired
    private AverageComputedField averageComputedField;
    @Autowired
    private AdditiveComputedField additiveComputedField;
    @Autowired
    private SumComputedField sumComputedField;
    @Autowired
    private NoOpsComputedField noOpsComputedField;

    public IComputedField getInstance(String className){

        if(className.equalsIgnoreCase(percentageComputedField.getClass().getSimpleName())){
            return percentageComputedField;

        } else if(className.equalsIgnoreCase(averageComputedField.getClass().getSimpleName())) {
            return averageComputedField;

        } else if(className.equalsIgnoreCase(additiveComputedField.getClass().getSimpleName())) {
            return additiveComputedField;

        } else if(className.equalsIgnoreCase(sumComputedField.getClass().getSimpleName())) {
            return sumComputedField;

        }else if(className.isEmpty()) {
            return noOpsComputedField;

        } else {
            throw new RuntimeException("Computer field not found for className "+className);
        }

    }

}

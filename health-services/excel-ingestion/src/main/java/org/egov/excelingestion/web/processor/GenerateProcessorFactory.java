package org.egov.excelingestion.web.processor;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class GenerateProcessorFactory {

    private final Map<String, IGenerateProcessor> processorMap = new HashMap<>();
    private final List<IGenerateProcessor> processors;

    public GenerateProcessorFactory(List<IGenerateProcessor> processors) {
        this.processors = processors;
    }

    @PostConstruct
    public void init() {
        for (IGenerateProcessor processor : processors) {
            processorMap.put(processor.getType(), processor);
        }
    }

    public IGenerateProcessor getProcessor(String type) {
        IGenerateProcessor processor = processorMap.get(type);
        if (processor == null) {
            throw new IllegalArgumentException("No processor found for type: " + type);
        }
        return processor;
    }
}

package digit.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

import java.util.*;
import lombok.extern.slf4j.Slf4j;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.feature.FeatureIterator;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GeoJsonService {

    private ObjectMapper objectMapper;

    public GeoJsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void parseJsonUsingLibrary() throws IOException {
        File file = new File("Microplan/Mozambiue.geojson");
        Map<String, Object> map = new HashMap<>();
        map.put("url", file.toURI().toURL());

        if (file.exists())
            log.info("File exists at - " + file.getAbsolutePath());
        else
            log.info("FILE NOT FOUND - " + file.getAbsolutePath());

        DataStore dataStore = DataStoreFinder.getDataStore(map);
        log.info("datastore --- " + dataStore);
//
//        String typeName = dataStore.getTypeNames()[0];
//        FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = dataStore.getFeatureSource(typeName);

//        FileDataStore fileDataStore = FileDataStoreFinder.getDataStore(file);
//        SimpleFeatureType schema = fileDataStore.getSchema();
//        System.out.println("Schema: " + schema);

    }

    public void parseJson() throws IOException {
        try {
            // Create a JSON factory and parser
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createParser(new File("Microplan/mozsmall.geojson"));

            // Create an object mapper to handle deserialization
            ObjectMapper mapper = new ObjectMapper();
            parser.setCodec(mapper); // Set the object mapper as the codec for the parser

            // Loop through the JSON tokens
            while (!parser.isClosed()) {
                JsonToken token = parser.nextToken();

                // Check if the token is a field name
                if (JsonToken.FIELD_NAME.equals(token)) {
                    String fieldName = parser.getCurrentName();
                    parser.nextToken(); // Move to the value token

                    // Check the field name
                    if ("type".equals(fieldName)) {
                        String type = parser.getText();
                        System.out.println("Type: " + type);
                    } else if ("properties".equals(fieldName)) {
                        // Parse the nested properties object
                        Map<Object, Object> properties = parseProperties(parser);

                        if(properties.containsKey("Population"))
                        {
                            Integer pop = (Integer) properties.get("Population");
                            log.info("Population - "+ pop);
                        }
                        System.out.println("Properties: " + properties);
                    }
                }
            }

            // Close the parser
            parser.close();
        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + e.getMessage());
        }
    }

    private static Map<Object, Object> parseProperties(JsonParser parser) throws IOException {
        Map<Object, Object> properties = new HashMap<>();
        // Loop through the properties object
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String key = parser.getCurrentName();
            parser.nextToken(); // Move to the value token
            Object value = parser.readValueAs(Object.class); // Deserialize the value
            properties.put(key, value); // Add the key-value pair to the map
        }
        return properties;
    }
}

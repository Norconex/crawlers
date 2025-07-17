package com.norconex.grid.calcite;

import java.util.HashMap;
import java.util.Map;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.grid.core.impl.CoreGridConnectorConfig;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CalciteGridConnectorConfig extends CoreGridConnectorConfig {

    //     private String calciteModelJson; // The JSON model as a string

    //     public String getCalciteModelJson() {
    //         return calciteModelJson;
    //     }
    //     public void setCalciteModelJson(String calciteModelJson) {
    //         this.calciteModelJson = calciteModelJson;
    //     }
    // }

    // // Write the JSON string to a temp file (Calcite expects a file or resource)
    // Path tempModelFile = Files.createTempFile("calcite-model",
    //         ".json");Files.writeString(tempModelFile,config.getCalciteModelJson());

    // Properties info =
    //         new Properties();info.put("model",tempModelFile.toAbsolutePath().toString());

    // Connection connection = DriverManager.getConnection("jdbc:calcite:", info);
    // // Now use the connection as usual

    private MultiTenancyMode multiTenancyMode = MultiTenancyMode.DATABASE;
    private final Map<String, Object> adapterProperties = new HashMap<>();
    private String adapterType = "jdbc";

    public CalciteGridConnectorConfig
            setAdapterProperties(Map<String, Object> adapterProperties) {
        CollectionUtil.setAll(this.adapterProperties, adapterProperties);
        return this;
    }
}

package com.socgen.riskweb;

import com.socgen.riskweb.vault.VaultService;
import org.json.JSONObject;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class RiskwebMaestroStartup {

    private static final org.apache.log4j.Logger LOGGER =
            org.apache.log4j.Logger.getLogger(RiskwebMaestroStartup.class);

    public static void main(String[] args) throws SQLException, ParseException {

        // 1️⃣ Fetch secrets from Vault
        VaultService vaultService = new VaultService();
        JSONObject secrets = vaultService.getSecretsList();

        if (secrets == null) {
            throw new RuntimeException("❌ Failed to fetch secrets from Vault. Startup aborted.");
        }

        // 2️⃣ Prepare dynamic properties
        Map<String, Object> props = new HashMap<>();
       // Database credentials
        props.put("jdbc.username", secrets.getString("username"));
        props.put("jdbc.password", secrets.getString("password"));
        
        // Maestro credentials
        props.put("maestro.client.id", secrets.getString("clientId"));
        props.put("maestro.secret.id", secrets.getString("secretId"));
        // 3️⃣ Create Spring Environment with these dynamic properties
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources propertySources = env.getPropertySources();
        propertySources.addFirst(new MapPropertySource("vaultProperties", props));

        // 4️⃣ Initialize Spring context *with that environment*
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.setEnvironment(env);
        ctx.register(ApplicationConfig.class);
        ctx.refresh();

        LOGGER.info("✅ Spring context initialized with Vault secrets.");

        // 5️⃣ Fetch beans
        RiskwebClientService riskwebClientService = ctx.getBean(RiskwebClientService.class);
        RestClientUtility restClientUtility = ctx.getBean(RestClientUtility.class);

        try {
            ResponseInternal internalResponse = restClientUtility.sendSubbookingApi();
            if (internalResponse != null) {
                LOGGER.info("Calling the Subbooking Api");
                riskwebClientService.saveSubbookingApi(internalResponse);
            }
        } catch (Exception e) {
            LOGGER.error("Error calling Subbooking API", e);
        }

        ctx.close();
    }
}

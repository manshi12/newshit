package com.socgen.iflow;

import com.socgen.vault.VaultServicePrd;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class AppStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppStarter.class);

    public static void main(String[] args) throws Exception {

        LOGGER.info("########## START: SPRING BOOT INITIALIZATION ##########");
        long startTime = System.currentTimeMillis();

        // 1️⃣ Fetch secrets from Vault
        VaultServicePrd vaultService = new VaultServicePrd();
        JSONObject secrets = vaultService.getSecretsList();

        if (secrets == null) {
            throw new RuntimeException("❌ Failed to fetch secrets from Vault. Startup aborted.");
        }

        // 2️⃣ Map Vault secrets to Spring property keys
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.username", secrets.getString("Pg_user"));
        props.put("spring.datasource.password", secrets.getString("Pg_pass"));
        props.put("client.id", secrets.getString("Monitor_client"));
        props.put("client.secret", secrets.getString("Monitor_client_secret"));

        // 3️⃣ Inject secrets into Spring environment BEFORE context starts
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        sources.addFirst(new MapPropertySource("vaultProperties", props));

        // 4️⃣ Start Spring Boot with secure environment
        SpringApplication app = new SpringApplication(AppStarter.class);
        app.setEnvironment(env);
        ApplicationContext ctx = app.run(args);

        long endTime = System.currentTimeMillis();
        LOGGER.info("✅ Spring Boot initialized in {} ms", (endTime - startTime));

        LOGGER.info("########## END: SPRING BOOT INITIALIZATION ##########");
    }
}

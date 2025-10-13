package com.sgcib.ris.searchscreen;

import com.sgcib.ris.searchscreen.vault.VaultServicePrd;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@SpringBootApplication
@ComponentScan({"com.sgcib.ris.searchscreen"})
public class AppStarter {

    public static void main(String[] args) throws Exception {

        // 1️⃣ Fetch secrets from Vault
        VaultServicePrd vaultService = new VaultServicePrd();
        JSONObject secrets = vaultService.getSecretsList();

        if (secrets == null) {
            throw new RuntimeException("❌ Failed to fetch secrets from Vault. Startup aborted.");
        }

        // 2️⃣ Map secrets to Spring properties
        Map<String, Object> props = new HashMap<>();
        props.put("jdbc.username", secrets.getString("Pg_user"));
        props.put("jdbc.password", secrets.getString("Pg_pass"));
        props.put("maestro.client.id", secrets.getString("Monitor_client"));
        props.put("maestro.secret.id", secrets.getString("Monitor_client_secret"));
        props.put("sgconn.client", secrets.getString("Sgconn_clinet"));
        props.put("key.store.pass", secrets.getString("key_store_pass"));

        // 3️⃣ Inject them into Spring before starting
        SpringApplication app = new SpringApplication(AppStarter.class);
        app.setDefaultProperties(props);

        ApplicationContext ctx = app.run(args);
        log.info("✅ Application started successfully with Vault secrets loaded.");
    }
}

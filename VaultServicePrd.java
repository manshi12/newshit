package com.sgcib.ris.searchscreen.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class VaultServicePrd {

    private static final Logger logger = LoggerFactory.getLogger(VaultServicePrd.class);

    private static final String VAULT_URL = "https://vault.cloud.socgen/v1";
    private static final String VAULT_NAMESPACE = "/myVault/DBE_2351_PRD_myvault_dbe";
    private static final String VAULT_NAME = "/kv/data/Tomtra";

    private static final String ROLE_ID = "afc86b5a-3403-0b7a-29ab-aa24faea40c6";
    private static final String SECRET_ID = "a56814c9-8b23-7e8f-71cd-475fdf10b47c";

    private String vaultToken;
    private long expiresIn;

    // --- Get Vault Token ---
    private String getToken() {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("role_id", ROLE_ID);
            body.put("secret_id", SECRET_ID);

            Map<String, String> headers = new HashMap<>();
            headers.put("X-Vault-Namespace", VAULT_NAMESPACE);
            headers.put("Content-Type", "application/json");

            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(body);

            HttpURLConnection request = buildRequest(
                    "POST",
                    new URL(VAULT_URL + "/auth/approle/login"),
                    headers,
                    jsonBody
            );

            if (request.getResponseCode() == 200 || request.getResponseCode() == 201) {
                String response = getResponse(request);
                JSONObject json = new JSONObject(response);
                JSONObject auth = json.getJSONObject("auth");
                vaultToken = auth.getString("client_token");
                expiresIn = System.currentTimeMillis() + (auth.getLong("lease_duration") * 1000);
                return vaultToken;
            } else {
                logger.error("Failed to get Vault token. HTTP {}", request.getResponseCode());
            }
        } catch (Exception e) {
            logger.error("Vault token generation failed", e);
        }
        return null;
    }

    private String getAccessToken() {
        if (vaultToken == null || System.currentTimeMillis() > expiresIn - 60000) {
            vaultToken = getToken();
        }
        return vaultToken;
    }

    private HttpURLConnection buildRequest(String method, URL url, Map<String, String> headers, String content) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        headers.forEach(conn::setRequestProperty);

        if (content != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(content.getBytes());
            }
        }

        return conn;
    }

    private String getResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Fetch secrets from Vault (Tomtra KV path)
     */
    public JSONObject getSecretsList() {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-Vault-Token", getAccessToken());

            URL url = new URL(VAULT_URL + VAULT_NAMESPACE + VAULT_NAME);
            HttpURLConnection request = buildRequest("GET", url, headers, null);

            if (request.getResponseCode() != 200) {
                logger.error("Vault request failed: HTTP {}", request.getResponseCode());
                return null;
            }

            String response = getResponse(request);
            JSONObject json = new JSONObject(response);
            JSONObject data = json.getJSONObject("data").getJSONObject("data");

            logger.info("Successfully retrieved secrets from Vault.");
            return data;
        } catch (Exception e) {
            logger.error("Failed to retrieve secrets from Vault", e);
            return null;
        }
    }
}

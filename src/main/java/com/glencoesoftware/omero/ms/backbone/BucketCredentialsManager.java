package com.glencoesoftware.omero.ms.backbone;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map.Entry;

import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class BucketCredentialsManager implements CredentialsManager {


    private static final org.slf4j.Logger log = LoggerFactory
            .getLogger(BucketCredentialsManager.class);

    private JsonObject credsObj;

    public BucketCredentialsManager(String credsFilePath) throws IOException {
        String credsJsonStr = new String(Files.readAllBytes(
                Paths.get(credsFilePath)));
        credsObj = new JsonObject(credsJsonStr);
    }

    @Override
    public String injectCredentials(JsonObject zarrJson) {
        String zarrPath = zarrJson.getString("zarrPath");
        if (!zarrPath.startsWith("s3://")) {
            return zarrPath;
        }
        Iterator<Entry<String, Object>> itr = credsObj.iterator();
        String bucketName = null;
        while (itr.hasNext()) {
            String thisBucket = itr.next().getKey();
            if (zarrPath.contains(thisBucket)) {
                bucketName = thisBucket;
                break;
            }
        }
        if (bucketName == null) {
            log.error("No credentials for path " + zarrPath);
            return zarrPath;
        }
        try {
            JsonObject bucketCreds = credsObj.getJsonObject(bucketName);
            String accessKey = bucketCreds.getString("access_key");
            accessKey = URLEncoder.encode(accessKey, StandardCharsets.UTF_8.toString());
            String secretKey = bucketCreds.getString("secret_key");
            secretKey = URLEncoder.encode(secretKey, StandardCharsets.UTF_8.toString());
            String s3Path = zarrPath.replace("s3://", "");
            StringBuilder pathWithCreds = new StringBuilder();
            pathWithCreds.append("s3://")
                .append(accessKey)
                .append(":")
                .append(secretKey)
                .append("@")
                .append(s3Path);
            zarrPath = pathWithCreds.toString();
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to encode credentials for path " + zarrPath);
            return null;
        }
        return zarrPath;
    }

}

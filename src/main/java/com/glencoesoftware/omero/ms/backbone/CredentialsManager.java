package com.glencoesoftware.omero.ms.backbone;

import io.vertx.core.json.JsonObject;

public interface CredentialsManager {
    public String injectCredentials(JsonObject zarrJson);
}

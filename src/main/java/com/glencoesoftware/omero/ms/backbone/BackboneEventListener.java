package com.glencoesoftware.omero.ms.backbone;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import ome.model.IObject;
import org.hibernate.event.AbstractCollectionEvent;
import org.hibernate.event.PostCollectionRemoveEvent;
import org.hibernate.event.PostCollectionRemoveEventListener;
import org.hibernate.event.PostCollectionUpdateEvent;
import org.hibernate.event.PostCollectionUpdateEventListener;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class BackboneEventListener implements
        PostCollectionUpdateEventListener,
        PostCollectionRemoveEventListener {

    public static final String MODEL_CHANGE_EVENT = "ome.model.change";

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(BackboneEventListener.class);

    private EventBus eventBus;

    public BackboneEventListener() {
        super();
        log.info("Registering BackboneEventListener...");
        Vertx vertx = Vertx.vertx();
        eventBus = vertx.eventBus();
    }

    @Override
    public void onPostUpdateCollection(PostCollectionUpdateEvent postCollectionUpdateEvent) {
        log.info("Collection updated!");
        notifyCollectionChange(postCollectionUpdateEvent);
    }

    @Override
    public void onPostRemoveCollection(PostCollectionRemoveEvent postCollectionRemoveEvent) {
        log.info("Collection removed!");
        // notifyCollectionChange(postCollectionRemoveEvent);
    }

    private void notifyCollectionChange(AbstractCollectionEvent event) {
        JsonArray changes = new JsonArray();

        String ownerName = event.getAffectedOwnerEntityName();
        Long ownerId = (Long) event.getAffectedOwnerIdOrNull();
        log.info("Collection changed: {}({})", ownerName, ownerId);
        JsonObject owner = new JsonObject();
        owner.put("entityType", ownerName);
        owner.put("entityId", ownerId);
        changes.add(owner);

        Collection<?> entities =
                (Collection<?>) event.getCollection();
        for (Object obj : entities) {
            if (obj instanceof IObject) {
                IObject iobj = (IObject) obj;
                String entityName = iobj.getClass().getName();
                Long entityId = iobj.getId();
                log.info("Entity changed: {}({})", entityName, entityId);
                JsonObject entity = new JsonObject();
                entity.put("entityType", entityName);
                entity.put("entityId", entityId);
                changes.add(entity);
            }
        }
        eventBus.publish(MODEL_CHANGE_EVENT, changes);
    }
}

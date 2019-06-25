package com.glencoesoftware.omero.ms.backbone;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import ome.model.IObject;
import org.hibernate.HibernateException;
import org.hibernate.event.AbstractCollectionEvent;
import org.hibernate.event.PostCollectionRemoveEvent;
import org.hibernate.event.PostCollectionRemoveEventListener;
import org.hibernate.event.PostCollectionUpdateEvent;
import org.hibernate.event.PostCollectionUpdateEventListener;
import org.hibernate.event.PostInsertEvent;
import org.hibernate.event.PostInsertEventListener;
import org.hibernate.event.PostUpdateEvent;
import org.hibernate.event.PostUpdateEventListener;
import org.hibernate.event.SaveOrUpdateEvent;
import org.hibernate.event.SaveOrUpdateEventListener;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class BackboneEventListener implements
        SaveOrUpdateEventListener,
        PostInsertEventListener,
        PostUpdateEventListener,
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
        notifyCollectionChange(postCollectionRemoveEvent);
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

        if (event.getCollection() instanceof Collection) {
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
        } else if (event.getCollection() instanceof Map) {
            Map<?, ?> entityMap = (Map<?, ?>) event.getCollection();
            log.info("Map changed: {}", entityMap.getClass());
        }
        eventBus.publish(MODEL_CHANGE_EVENT, changes);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent postUpdateEvent) {
        log.info("Updated Entity: {}", postUpdateEvent.getEntity());
    }

    @Override
    public void onSaveOrUpdate(SaveOrUpdateEvent saveOrUpdateEvent) throws HibernateException {
        log.info("Save or Update of Entity: {}", saveOrUpdateEvent.getEntity());
    }

    @Override
    public void onPostInsert(PostInsertEvent postInsertEvent) {
        log.info("Entity Inserted: {}", postInsertEvent.getEntity());
    }
}

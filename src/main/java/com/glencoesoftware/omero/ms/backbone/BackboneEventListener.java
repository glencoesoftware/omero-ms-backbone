package com.glencoesoftware.omero.ms.backbone;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import ome.model.IObject;

import org.apache.commons.lang.ArrayUtils;
import org.hibernate.event.EventListeners;
import org.hibernate.event.PostDeleteEvent;
import org.hibernate.event.PostDeleteEventListener;
import org.hibernate.event.PostInsertEvent;
import org.hibernate.event.PostInsertEventListener;
import org.hibernate.event.PostUpdateEvent;
import org.hibernate.event.PostUpdateEventListener;
import org.hibernate.impl.SessionFactoryImpl;
import org.slf4j.LoggerFactory;

public class BackboneEventListener extends AbstractVerticle implements
        PostInsertEventListener,
        PostUpdateEventListener,
        PostDeleteEventListener {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(BackboneEventListener.class);

    private final SessionFactoryImpl sessionFactory;

    BackboneEventListener(SessionFactoryImpl sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Entry point method which starts the server event loop.
     * @param args Command line arguments.
     */
    @Override
    public void start() {
        log.info("Starting verticle");
        log.info("Registering Hibernate Event Listeners");
        EventListeners listeners = sessionFactory.getEventListeners();
        listeners.setPostInsertEventListeners(
                (PostInsertEventListener[]) ArrayUtils.add(listeners.getPostInsertEventListeners(), this));
        listeners.setPostUpdateEventListeners(
                (PostUpdateEventListener[]) ArrayUtils.add(listeners.getPostUpdateEventListeners(), this));
        listeners.setPostDeleteEventListeners(
                (PostDeleteEventListener[]) ArrayUtils.add(listeners.getPostDeleteEventListeners(), this));
    }

    @Override
    public void onPostInsert(PostInsertEvent postInsertEvent) {
        Object obj = postInsertEvent.getEntity();
        log.debug("Insert event for entity: {}", obj);
        publishEvent(obj, "insert");
    }

    @Override
    public void onPostUpdate(PostUpdateEvent postUpdateEvent) {
        Object obj = postUpdateEvent.getEntity();
        log.debug("Update event for entity: {}", obj);
        publishEvent(obj, "update");
    }

    @Override
    public void onPostDelete(PostDeleteEvent postDeleteEvent) {
        Object obj = postDeleteEvent.getEntity();
        log.debug("Delete event for entity: {}", obj);
        publishEvent(obj, "delete");
    }

    private void publishEvent(Object obj, String changeType) {
        if (obj instanceof IObject) {
            IObject iobj = (IObject) obj;
            String entityType = iobj.getClass().getName();
            Long entityId = iobj.getId();
            JsonObject event = new JsonObject();
            event.put("changeType", changeType);
            event.put("entityType", entityType);
            event.put("entityId", entityId);
            vertx.eventBus().publish(
                    BackboneVerticle.MODEL_CHANGE_EVENT, event);
        }
    }
}

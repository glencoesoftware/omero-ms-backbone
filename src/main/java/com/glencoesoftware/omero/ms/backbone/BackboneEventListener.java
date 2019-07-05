package com.glencoesoftware.omero.ms.backbone;

import java.util.Optional;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import ome.model.IObject;
import ome.system.PreferenceContext;

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
    private PreferenceContext preferenceContext;

    BackboneEventListener(PreferenceContext preferenceContext, SessionFactoryImpl sessionFactory) {
        this.preferenceContext = preferenceContext;
        this.sessionFactory = sessionFactory;
    }

    /**
     * Entry point method which starts the server event loop.
     */
    @Override
    public void start() {
        log.info("Starting verticle");
        JsonArray eventListeners =
                new JsonArray(Optional.ofNullable(preferenceContext.getProperty(
                        "omero.ms.backbone.event_listeners"
                )).orElse("[]"));
        log.info("Registering Hibernate Event Listeners: {}", eventListeners);
        EventListeners listeners = sessionFactory.getEventListeners();
        if (eventListeners.contains("INSERT")) {
            listeners.setPostInsertEventListeners(
                    (PostInsertEventListener[]) ArrayUtils.add(listeners.getPostInsertEventListeners(), this));
            log.debug("Insert event listener registered.");
        }
        if (eventListeners.contains("UPDATE")) {
            listeners.setPostUpdateEventListeners(
                    (PostUpdateEventListener[]) ArrayUtils.add(listeners.getPostUpdateEventListeners(), this));
            log.debug("Update event listener registered.");
        }
        if (eventListeners.contains("DELETE")) {
            listeners.setPostDeleteEventListeners(
                    (PostDeleteEventListener[]) ArrayUtils.add(listeners.getPostDeleteEventListeners(), this));
            log.debug("Delete event listener registered.");
        }
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

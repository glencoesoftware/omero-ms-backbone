/*
 * Copyright (C) 2018 Glencoe Software, Inc. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.glencoesoftware.omero.ms.backbone;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import ome.api.IPixels;
import ome.api.IQuery;
import ome.conditions.RemovedSessionException;
import ome.conditions.SessionTimeoutException;
import ome.io.nio.OriginalFilesService;
import ome.model.IObject;
import ome.model.annotations.FileAnnotation;
import ome.model.core.Image;
import ome.model.core.OriginalFile;
import ome.model.core.Pixels;
import ome.model.display.RenderingDef;
import ome.parameters.Parameters;
import ome.services.sessions.SessionManager;
import ome.services.util.Executor;
import ome.system.Principal;
import ome.system.ServiceFactory;
import ome.util.SqlAction;
import omero.ServerError;
import omero.util.IceMapper;
import ome.services.blitz.repo.FileMaker;
import ome.services.blitz.repo.LegacyRepositoryI;
import ome.services.blitz.repo.path.FilePathRestrictionInstance;
import ome.services.blitz.repo.path.FilePathRestrictions;
import ome.services.blitz.repo.path.FsFile;
import ome.services.blitz.repo.path.MakePathComponentSafe;
import ome.services.blitz.repo.path.ServerFilePathTransformer;


/**
 * Main entry point for the OMERO microservice architecture backbone verticle.
 * <b>NOTE:</b> As this verticle is being instantiated by Spring 3 inside the
 * OMERO server it <b>CANNOT</b> contain any Java 8+ lambda expressions in
 * directly accessible code paths.  If you are seeing
 * {@link ArrayIndexOutOfBoundsException}'s thrown from Spring ASM during bean
 * instantiation, check for lambda expressions.
 * @author Chris Allan <callan@glencoesoftware.com>
 *
 */
public class BackboneVerticle extends AbstractVerticle {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(BackboneVerticle.class);

    public static final String IS_SESSION_VALID_EVENT =
            "omero.is_session_valid";

    public static final String CAN_READ_EVENT =
            "omero.can_read";

    public static final String GET_OBJECT_EVENT =
            "omero.get_object";

    public static final String GET_ALL_ENUMERATIONS_EVENT =
            "omero.get_all_enumerations";

    public static final String GET_RENDERING_SETTINGS_EVENT =
            "omero.get_rendering_settings";

    public static final String GET_PIXELS_DESCRIPTION_EVENT =
            "omero.get_pixels_description";

    public static final String GET_FILE_PATH_EVENT =
            "omero.get_file_path";

    public static final String GET_ORIGINAL_FILE_PATHS_EVENT =
            "omero.get_original_file_paths";

    public static final String GET_IMPORTED_IMAGE_FILES =
            "omero.get_imported_image_files";

    public static final String MODEL_CHANGE_EVENT =
            "ome.model.change";

    private final Executor executor;

    private final SessionManager sessionManager;

    private final DetailsContextsFilter contextsFilter =
            new DetailsContextsFilter();

    private final LegacyRepositoryI managedRepository;

    private final SqlAction sqlAction;

    private final FilePathRestrictions filePathRestrictions;

    /** Original file service for getting paths from the main repository */
    private final OriginalFilesService originalFilesService;

    private String managedRepositoryRoot;

    public BackboneVerticle(Executor executor,
            SessionManager sessionManager,
            SqlAction sqlAction,
            OriginalFilesService originalFilesService,
            LegacyRepositoryI managedRepository,
            String pathRules) {
        this.executor = executor;
        this.sessionManager = sessionManager;
        this.sqlAction = sqlAction;
        this.originalFilesService = originalFilesService;
        this.managedRepository = managedRepository;

        try {
            omero.model.OriginalFile description =
                    managedRepository.getDescription();
            managedRepositoryRoot = description.getPath().getValue() +
                    description.getName().getValue();
        } catch (ServerError e) {
            log.error("Error retrieving managed repository description", e);
            this.managedRepositoryRoot = null;
        }


        // File path restriction creation cribbed from PublicRepositoryI
        // constructor
        final Set<String> terms = new HashSet<String>();
        for (final String term : pathRules.split(",")) {
            if (StringUtils.isNotBlank(term)) {
                terms.add(term.trim());
            }
        }
        final String[] termArray = terms.toArray(new String[terms.size()]);
        try {
            this.filePathRestrictions =
                    FilePathRestrictionInstance.getFilePathRestrictions(
                            termArray);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(
                    "unknown rule set named in: " + pathRules);
        }
    }

    @Override
    public void start() {
        EventBus eventBus = vertx.eventBus();

        eventBus.<JsonObject>consumer(
            IS_SESSION_VALID_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    isSessionValid(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            CAN_READ_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    canRead(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            GET_OBJECT_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    getObject(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            GET_ALL_ENUMERATIONS_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    getAllEnumerations(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            GET_RENDERING_SETTINGS_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    getRenderingSettings(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            GET_PIXELS_DESCRIPTION_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    getPixelsDescription(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            GET_FILE_PATH_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    getFilePath(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            GET_ORIGINAL_FILE_PATHS_EVENT, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    getOriginalFilePaths(event);
                };
            }
        );
        eventBus.<JsonObject>consumer(
            GET_IMPORTED_IMAGE_FILES, new Handler<Message<JsonObject>>() {
                public void handle(Message<JsonObject> event) {
                    getImportedImageFiles(event);
                };
            }
        );
    }

    private void handleMessageWithJob(BackboneSimpleWork job) {
        Message<JsonObject> message = job.getMessage();
        JsonObject data = message.body();
        String sessionKey = data.getString("sessionKey");
        try {
            ome.model.meta.Session session = null;
            try {
                 session = sessionManager.find(sessionKey);
            } catch (RemovedSessionException | SessionTimeoutException e) {
                // No-op
            }
            if (session == null) {
                message.fail(403, "Session invalid");
                return;
            }
            Principal principal = new Principal(
                    session.getUuid(),
                    "-1",
                    session.getDefaultEventType());
            Object o = executor.execute(principal, job);
            // May contain non-serializable objects
            contextsFilter.filter("", o);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(o);
            }
            message.reply(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failure encoding", e);
            message.fail(500, e.getMessage());
        }
    }

    private void isSessionValid(Message<JsonObject> message) {
        String sessionKey = message.body().getString("sessionKey");
        try {
            message.reply(new Boolean(sessionManager.find(sessionKey) != null));
        } catch (Exception e) {
            message.reply(Boolean.FALSE);
        }
    }

    private void canRead(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "canRead") {
            @Transactional(readOnly = true)
            public Boolean doWork(Session session, ServiceFactory sf) {
                try {
                    JsonObject data = this.getMessage().body();
                    IQuery iQuery = sf.getQueryService();
                    Class<? extends IObject> klass =
                            IceMapper.omeroClass(
                                    data.getString("type"), true);
                    message.reply(new Boolean(
                            iQuery.find(klass, data.getLong("id")) != null));
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getObject(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getObject") {
            @Transactional(readOnly = true)
            public IObject doWork(Session session, ServiceFactory sf) {
                try {
                    JsonObject data = this.getMessage().body();
                    IQuery iQuery = sf.getQueryService();
                    Class<? extends IObject> klass =
                            IceMapper.omeroClass(
                                    data.getString("type"), true);
                    return iQuery.get(klass, data.getLong("id"));
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getAllEnumerations(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getAllEnumerations") {
            @Transactional(readOnly = true)
            public List<? extends IObject> doWork(Session session, ServiceFactory sf) {
                try {
                    IPixels iPixels = sf.getPixelsService();
                    JsonObject data = this.getMessage().body();
                    Class<? extends IObject> klass =
                            IceMapper.omeroClass(
                                    data.getString("type"), true);
                    return iPixels.getAllEnumerations(klass);
                } catch (Exception e) {
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getRenderingSettings(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getRenderingSettings") {
            @Transactional(readOnly = true)
            public RenderingDef doWork(Session session, ServiceFactory sf) {
                try {
                    IPixels iPixels = sf.getPixelsService();
                    JsonObject data = this.getMessage().body();
                    Long pixelsId = data.getLong("pixelsId");
                    return iPixels.retrieveRndSettings(pixelsId);
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getPixelsDescription(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getPixelsDescription") {
            @Transactional(readOnly = true)
            public Pixels doWork(Session session, ServiceFactory sf) {
                try {
                    IQuery iQuery = sf.getQueryService();
                    IPixels iPixels = sf.getPixelsService();
                    JsonObject data = this.getMessage().body();
                    Parameters parameters = new Parameters();
                    parameters.addId(data.getLong("imageId"));
                    Image image = iQuery.findByQuery(
                            "SELECT i FROM Image as i " +
                            "JOIN FETCH i.pixels " +
                            "WHERE i.id = :id",
                            parameters);
                    Pixels pixels = iPixels.retrievePixDescription(
                            image.getPrimaryPixels().getId());
                    pixels.setImage(image);
                    return pixels;
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getImportedImageFiles(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getFileset") {
            @Transactional(readOnly = true)
            public List<OriginalFile> doWork(Session session, ServiceFactory sf) {
                try {
                    IQuery iQuery = sf.getQueryService();
                    JsonObject data = this.getMessage().body();
                    Parameters parameters = new Parameters();
                    parameters.addId(data.getLong("imageId"));
                    List<OriginalFile> originalFiles = iQuery.findAllByQuery(
                            "SELECT ofile FROM FilesetEntry as fse " +
                            "JOIN fse.fileset as fileset " +
                            "JOIN fse.originalFile as ofile " +
                            "JOIN fileset.images as i " +
                            "WHERE i.id in (:id)",
                            parameters);
                    return originalFiles;
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getFilePath(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getFilePath") {
            @Transactional(readOnly = true)
            public String doWork(Session session, ServiceFactory sf) {
                try {
                    JsonObject data = this.getMessage().body();
                    IQuery iQuery = sf.getQueryService();
                    OriginalFile of = null;
                    if (data.getString("type").equals("FileAnnotation")) {
                        FileAnnotation fa = iQuery.get(
                                FileAnnotation.class, data.getLong("id"));
                        of = fa.getFile();
                    } else {
                        of = iQuery.get(OriginalFile.class, data.getLong("id"));
                    }
                    if (of.getRepo() == null) {
                        return originalFilesService.getFilesPath(of.getId());
                    } else {
                        String path = getOriginalFilePath(of);
                        if (path == null) {
                            FsFile fsFile = new FsFile(sqlAction.findRepoFilePath(
                                    of.getRepo(), of.getId()));
                            message.fail(
                                404, "Illegal path: " + fsFile.toString());
                        }
                        return path;
                    }
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private void getOriginalFilePaths(Message<JsonObject> message) {
        BackboneSimpleWork job = new BackboneSimpleWork(message, this, "getOriginalFilePaths") {
            @Transactional(readOnly = true)
            public String doWork(Session session, ServiceFactory sf) {
                try {
                    JsonObject data = this.getMessage().body();
                    IQuery iQuery = sf.getQueryService();
                    JsonArray fileIds = data.getJsonArray("originalFileIds");
                    Iterator<Object> iter = fileIds.iterator();
                    JsonArray paths = new JsonArray();
                    while (iter.hasNext()) {
                        Long id = new Long(((Integer) iter.next()).toString());
                        OriginalFile of = iQuery.get(OriginalFile.class, id);
                        if (of.getRepo() == null) {
                            paths.add(originalFilesService.getFilesPath(of.getId()));
                        } else {
                            String path = getOriginalFilePath(of);
                            if (path == null) {
                                FsFile fsFile = new FsFile(sqlAction.findRepoFilePath(
                                        of.getRepo(), of.getId()));
                                message.fail(
                                    404, "Illegal path: " + fsFile.toString());
                                return null;
                            }
                            paths.add(path);
                        }
                    }
                    JsonObject responseObj = new JsonObject();
                    responseObj.put("managedRepositoryRoot", managedRepositoryRoot);
                    responseObj.put("paths", paths);
                    return responseObj.toString();
                } catch (Exception e) {
                    log.error("Error retrieving data", e);
                    message.fail(500, e.getMessage());
                }
                return null;
            }
        };
        handleMessageWithJob(job);
    }

    private String getOriginalFilePath(OriginalFile of) throws ServerError {
        FsFile fsFile = new FsFile(sqlAction.findRepoFilePath(
                of.getRepo(), of.getId()));
        // (1) Create a FileMaker like in the LegacyRepositoryI
        //     constructors which directly initialize the
        //     ManagedRepositoryI servant (a subclass of
        //     PublicRepositoryI) with this instance
        FileMaker fileMaker = new FileMaker(managedRepositoryRoot);
        // (2) Prepare a file path transformer as in
        //     PublicRepositoryI#initialize() used by
        //     PublicRepository#checkId() and by extension
        //     the CheckedPath constructor which is the final
        //     implementation we wish to mimic
        ServerFilePathTransformer serverPaths =
                new ServerFilePathTransformer();
        serverPaths.setBaseDirFile(
                new File(fileMaker.getDir()));
        serverPaths.setPathSanitizer(new MakePathComponentSafe(
                filePathRestrictions));
        if (!serverPaths.isLegalFsFile(fsFile)) {
            return null;
        }
        return serverPaths.getServerFileFromFsFile(fsFile)
            .getAbsolutePath();
    }

}

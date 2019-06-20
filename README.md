[![AppVeyor status](https://ci.appveyor.com/api/projects/status/github/omero-ms-backbone)](https://ci.appveyor.com/project/gs-jenkins/omero-ms-backbone)

OMERO Microservice Backbone
===========================

OMERO Vert.x asynchronous microservice service infrastructure for integration
with an OMERO server instance.  The "backbone" services host a layer that
provides endpoints, accessible over the Vert.x eventbus, that expose various
OMERO server functionality. 

This infrastructure relies on the OMERO server extension mechanism::

* https://docs.openmicroscopy.org/omero/5.4.10/developers/Server/ExtendingOmero.html

Requirements
============

* OMERO 5.4.x+
* Java 8+

Workflow
========

The microservice service infrastructure relies on the following workflow::

1. Placement of all JARs from `lib` folder of the distribution into the
`lib/server` folder of your OMERO instance

1. Configuring `omero.ms.backbone.cluster_host` OMERO server configuration
property if required

1. Configuration of Hazelcast via the `hazelcast.xml.example` enclosed in the
distribution and placement in the `etc` folder of your OMERO instance

1. Symlinking of the `omero-ms-backbone*.jar` to `extensions.jar` in order to
activate the infrastructure

1. (Optional) In order to enable the Hibernate Event listeners and propagate 
events from Hibernate to HazelCast, the `BackBoneEventListener` needs to be 
registered with Hibernate. To do this, it is necessary to edit the 
`model-psql.jar` in the `lib/server` directory. Some tools (e.g. Vim) are 
capable of editing the JAR in place, otherwise it is necessary to unpack, 
edit, and re-zip the JAR. The following lines must be added to `hibernate.cfg
.xml` within that JAR:
```xml
<listener type="post-collection-update" class="com.glencoesoftware.omero.ms.backbone.BackboneEventListener"/>
<listener type="post-collection-remove" class="com.glencoesoftware.omero.ms.backbone.BackboneEventListener"/>
```

1. Restarting your OMERO server

Configuring Logging
===================

Logging is provided using the logback library and piggybacks on the OMERO server
configuration.  You can configure logging by editing the `etc/logback.xml` of
your OMERO instance and adding loggers for `com.glencoesoftware.omero.ms` and/or
`com.hazelcast`::

    ...
    <logger name="com.glencoesoftware.omero.ms" level="DEBUG"/>
    <logger name="com.hazelcast" level="DEBUG"/>
    ...

Development Installation
========================

1. Clone the repository::

        git clone git@github.com:glencoesoftware/omero-ms-backbone.git

1. Run the Gradle build and utilize the artifacts as required::

        ./gradlew installDist
        cd build/install
        ...

1. Log in to the OMERO instance you would like to develop against with and 
extract a session key.. This can be done using the `login` and `sessions`
command line plugins for example::

        $ bin/omero login
        Previous session expired for root on localhost:4064
        Server: [localhost:4064]
        Username: [root]
        Password:
        Created session for root@localhost:4064. Idle timeout: 10 min. Current group: system
        $ bin/omero sessions list
         Server         | User | Group  | Session                              | Active    | Started
        ----------------+------+--------+--------------------------------------+-----------+--------------------------
         localhost:4064 | root | system | 97db5b88-5d7e-40f8-883b-17cf549f6126 | Logged in | Fri Feb  1 13:58:38 2019
        (1 row)

1. Run single or multiple image region tests using `curl`::

        curl http://localhost:9090/api/<session_key>/...

Eclipse Configuration
=====================

Used for testing the various endpoints.

1. Run the Gradle Eclipse task::

        ./gradlew eclipse

1. Add a new Run Configuration with a main class of `io.vertx.core.Starter`::

        run "com.glencoesoftware.omero.ms.backbone.QueryVerticle" -cluster -cluster-host <cluster_host>

Running Tests
=============

Using Gradle run the unit tests:

    ./gradlew test

Reference
=========

* https://lettuce.io/
* http://vertx.io/

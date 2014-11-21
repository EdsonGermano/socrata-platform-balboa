# Balboa

**Balboa** is Socrata's internal tenant metrics system. It provides a JMS-based service for inserting metrics into a Cassandra cluster, with an HTTP REST API for metric retrieval.

## Usage
### Client
**Balboa-client** is a library which handles insertion of metrics into a JMS queue for consumption by balboa-jms.  To include it in a project:

1. Add the Socrata release repository to the project's `build.sbt`:
```
resolvers := Seq(
  ...
  "socrata maven" at "https://repository-socrata-oss.forge.cloudbees.com/release",
  ...
)
```
2. Add the balboa-client dependency to the project's library dependencies:
```
libraryDependencies ++= Seq(
  ...
  "com.socrata" % "balboa-client" % "0.13.0",
  ...
)
```

### Server
**Balboa server** requires four separate components to function properly:

* [ActiveMQ](http://activemq.apache.org) (v5.5.1)
* A [Cassandra](http://cassandra.apache.org) cluster (v1.1.7)
* balboa-jms
* balboa-http

#### Setup (balboa-jms and balboa-http)

##### Configuration
Balboa has default configuration files for each runnable project, under `[project]/src/main/resources`.  To override any of these settings, create the file `/etc/balboa.properties` and add overrides as needed.

##### Building and Running

1. From the project root, run `sbt assembly`. This will produce two standalone assembly jars:
`balboa-jms/target/scala-2.10/balboa-jms-assembly-[VERSION].jar` and `balboa-http/target/scala-2.10/balboa-http-assembly-[VERSION].jar`, where VERSION is the current version of the project as defined in `version.sbt`.

2. Assuming Cassandra and ActiveMQ are installed, create the Cassandra schema:
`cassandra-cli < etc/balboa-cassandra.schema`. By default, this creates the `Metrics2012` keyspace. If a different keyspace is desired, tweak the schema file as necessary.

3. Start balboa-jms with `java -jar [JAR] [JMS_SERVER] [KEYSPACE]`. In a typical developer setup, this might look something like `java -jar balboa-jms/target/scala-2.10/balboa-jms-assembly-[VERSION].jar failover:tcp://localhost:61616 Metrics2012`.

4. Start balboa-http with `java -jar [JAR] [PORT]`. In a typical developer setup, this might look something like `java -jar balboa-http/target/scala-2.10/balboa-http-assembly-[VERSION].jar 2012`.

At this point, balboa-client should be capable of depositing metrics into balboa server, which can then be queried through the REST API.

##### Admin Tool

BalboaAdmin is a tool for dumping and loading metrics. It is extemely useful for correcting metrics which have been incorrectly set; or for migrating metrics across environments.

```
> java -cp balboa-admin-assembly-0.14.1-SNAPSHOT.jar com.socrata.balboa.admin.BalboaAdmin
Balboa admin utility:
       java -jar balboa-admin <command> [args]

Commands:
	fsck [filters...]  : Check the balboa file system and validate the correctness of the tiers. This will probably take a long time.
	fill file          : Restore balboa metrics from [file].
	dump [filters...]  : Dump all of the data in a balboa store to stdout in a format suitable for fill, with an optional entity regex
	dump-only entityId : Dump a specific entity in a format suitable for fill
	list [filters...]  : Dump all of the entity keys in a balboa store to stdout, with an optional entity regex
```

# Example: Dump Metrics for Entity "foo"

```
java -cp balboa-admin-assembly-0.14.1-SNAPSHOT.jar com.socrata.balboa.admin.BalboaAdmin dump foo
"foo","1414875600000","datasets","absolute","387"
"foo","1414879200000","datasets","absolute","387"
"foo","1414882800000","datasets","absolute","387"
"foo","1414886400000","datasets","absolute","387"
...
```
# Example: Fill Metrics for Entity "foo"

```
java -cp balboa-admin-assembly-0.14.1-SNAPSHOT.jar com.socrata.balboa.admin.BalboaAdmin fill dataset.counts.out.fixed
Reading from file dataset.counts.out.fixed
INFO - Persisted entity: foo with 1 absolute and 0 aggregated metrics - took 73ms
INFO - Persisted entity: foo with 1 absolute and 0 aggregated metrics - took 15ms
INFO - Persisted entity: foo with 1 absolute and 0 aggregated metrics - took 14ms
```

## Releases

As of 0.13.0, balboa is versioned with [Semantic Versioning](http://www.semver.org), and uses the [sbt-release](https://github.com/sbt/sbt-release) plugin. To release balboa, run `sbt release` from the project root.

## License
Balboa is licensed under the [Apache 2.0](https://github.com/socrata/balboa/blob/master/LICENSE.md) license.

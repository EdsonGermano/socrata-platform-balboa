FROM socrata/java8

# Forward the ZooKeeper and Cassandra Port.
ENV BALBOA_PORT 2012
EXPOSE $BALBOA_PORT

### Environment Variables
ENV BALBOA_ROOT /srv/balboa-service-jms
ENV BALBOA_ARTIFACT balboa-service-jms-assembly.jar

# Default Values
ENV JAVA_XMX 1024m
ENV JAVA_MAX_METASPACE_SIZE 64m

WORKDIR $BALBOA_ROOT

ADD ship.d /etc/ship.d

COPY $BALBOA_ARTIFACT $BALBOA_ROOT/

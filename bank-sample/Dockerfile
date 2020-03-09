FROM raft/chainvayler:0.1 AS builder
 
COPY . /chainvayler-bank-sample

WORKDIR /chainvayler-bank-sample
RUN gradle clean instrument build copyDependencyJars

FROM openjdk:8-jre-alpine
# FROM openjdk:8

COPY --from=builder /chainvayler-bank-sample/build/libs/chainvayler-bank-sample-0.1.jar /chainvayler-bank-sample/libs/
COPY --from=builder /chainvayler-bank-sample/libs /chainvayler-bank-sample/libs

WORKDIR /chainvayler-bank-sample
RUN for jar in /chainvayler-bank-sample/libs/*; do CLASSPATH=$CLASSPATH:$jar; done && echo "CLASSPATH: $CLASSPATH"  && echo "$CLASSPATH" > /chainvayler-bank-sample/classpath  

# java -ea -Xmx1024m -Dhazelcast.logging.type=none -classpath "$CLASSPATH" raft.chainvayler.samples.bank.Main
# -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n
CMD exec java -ea -Xmx1024m -Dhazelcast.logging.type=jdk -cp `cat /chainvayler-bank-sample/classpath` raft.chainvayler.samples.bank.Main




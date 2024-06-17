package com.boba.gatling.simulations;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import ru.tinkoff.gatling.kafka.javaapi.protocol.KafkaProtocolBuilderNew;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static ru.tinkoff.gatling.kafka.javaapi.KafkaDsl.kafka;

public class ConstantUsersTests extends Simulation {

    private static final int END_USERS = parseEnvVar("RAMP_USERS", 1);
    private static final int DURATION = parseEnvVar("DURATION", 60);
    private static final int PAYLOAD_SIZE = parseEnvVar("PAYLOAD_SIZE", 10);

    private static int parseEnvVar(String varName, int defaultValue) {
        String varValue = System.getenv(varName);
        if (varValue != null && !varValue.isEmpty()) {
            try {
                return Integer.parseInt(varValue);
            } catch (NumberFormatException e) {
                System.err.println("Invalid integer value for environment variable " + varName + ": " + varValue);
            }
        }
        return defaultValue;
    }

    private final KafkaProtocolBuilderNew kafkaProtocol = kafka().requestReply()
                                                                 .producerSettings(
                                                                         Map.of(
                                                                                 ProducerConfig.ACKS_CONFIG, "1",
                                                                                 ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka.default.svc.cluster.local:9092"
                                                                         )
                                                                 )
                                                                 .consumeSettings(
                                                                         Map.of(
                                                                                 "bootstrap.servers", "kafka.default.svc.cluster.local:9092",
                                                                                 ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"
                                                                         )
                                                                 )
                                                                 .timeout(Duration.ofSeconds(60));

    private final AtomicInteger c = new AtomicInteger(0);
    private final Iterator<Map<String, Object>> feeder = Stream.generate(
            (Supplier<Map<String, Object>>) () -> Collections.singletonMap("key", c.incrementAndGet())
    ).iterator();

    private final Headers headers = new RecordHeaders();

    private final ScenarioBuilder constantUsers = scenario("Constant users")
            .feed(feeder)
            .exec(
                    kafka("ReqRep").requestReply()
                                   .requestTopic("gatling.requests")
                                   .replyTopic("gatling.responses")
                                   .send("#{key}",
                                         String.format("{\"messageId\": #{key}, \"size\": %d}", PAYLOAD_SIZE),
                                         headers, String.class, String.class)
                                   .check(substring("messageId=#{key}"))
            );

    {
        System.out.println("Setting up simulation with constant " + END_USERS + " users for " + DURATION + " seconds");
        setUp(
                constantUsers.injectOpen(constantUsersPerSec(END_USERS).during(Duration.ofSeconds(DURATION)))
        ).protocols(kafkaProtocol);
    }
}

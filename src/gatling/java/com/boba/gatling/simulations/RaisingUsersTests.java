package com.boba.gatling.simulations;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
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


public class RaisingUsersTests extends Simulation {

    private static final int START_RAMP_USERS = 0;
    private static final int END_RAMP_USERS = parseEnvVar("RAMP_USERS", 10);
    private static final int RAMP_DURATION = parseEnvVar("DURATION", 10);
    private static final int CONSTANT_DURATION = parseEnvVar("CONSTANT_DURATION", 10);
    private static final int PAYLOAD_SIZE = parseEnvVar("PAYLOAD_SIZE", 10);

    static int parseEnvVar(String varName, int defaultValue) {
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

    private final KafkaProtocolBuilderNew kafkaProtocolC = kafka().requestReply()
                                                                  .producerSettings(
                                                                          Map.of(
                                                                                  ProducerConfig.ACKS_CONFIG, "1",
                                                                                  ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                                                                  "localhost:9092"
                                                                          )
                                                                  )
                                                                  .consumeSettings(
                                                                          Map.of("bootstrap.servers", "localhost:9092")
                                                                  )
                                                                  .timeout(Duration.ofSeconds(5));

    private final AtomicInteger c = new AtomicInteger(0);

    private final Iterator<Map<String, Object>> feeder =
            Stream.generate((Supplier<Map<String, Object>>) () -> Collections.singletonMap("key", c.incrementAndGet())
                  )
                  .iterator();

    private final Headers headers = new RecordHeaders();

    private final ScenarioBuilder scn = scenario("Ramp users")
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
        System.out.println(
                "Setting up simulation with " + START_RAMP_USERS + " to " + END_RAMP_USERS + " users in " + RAMP_DURATION + " seconds");
        setUp(scn.injectOpen(rampUsersPerSec(START_RAMP_USERS).to(END_RAMP_USERS)
                                                              .during(RAMP_DURATION),
                             constantUsersPerSec(END_RAMP_USERS).during(CONSTANT_DURATION))
        ).protocols(kafkaProtocolC);
    }
}

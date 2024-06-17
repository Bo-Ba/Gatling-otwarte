package com.boba.gatling.simulations;

import com.boba.gatling.utils.Util;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import ru.tinkoff.gatling.kafka.javaapi.protocol.KafkaProtocolBuilderNew;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.boba.gatling.simulations.RaisingUsersTests.parseEnvVar;
import static io.gatling.javaapi.core.CoreDsl.*;
import static ru.tinkoff.gatling.kafka.javaapi.KafkaDsl.kafka;

public class SpikesUsersTests extends Simulation {

    private static final int CONSTANT_USERS = parseEnvVar("CONSTANT_USERS", 10);
    private static final int CONSTANT_DURATIONS = parseEnvVar("DURATION", 10);
    private static final int PAYLOAD_SIZE = parseEnvVar("PAYLOAD_SIZE", 10);
    private static final int PEEK_DURATION = parseEnvVar("PEEK_DURATION", 20);
    private static final int PEEK_RAMP_TIME = parseEnvVar("PEEK_RAMP_TIME", 2);
    private static final int PEEK_USERS = parseEnvVar("PEEK_USERS", 5 * CONSTANT_USERS);

    private final KafkaProtocolBuilderNew kafkaProtocol = kafka().requestReply()
                                                                 .producerSettings(
                                                                         Map.of(
                                                                                 ProducerConfig.ACKS_CONFIG, "1",
                                                                                 ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"
                                                                         )
                                                                 )
                                                                 .consumeSettings(
                                                                         Map.of("bootstrap.servers", "localhost:9092")
                                                                 )
                                                                 .timeout(Duration.ofSeconds(5));

    private final AtomicInteger idGenerator = new AtomicInteger(0);
    private final Headers headers = new RecordHeaders();

    private ScenarioBuilder customLoad = scenario("Custom Load Pattern")
            .exec(
                    kafka("ReqRep").requestReply()
                                   .requestTopic("gatling.requests")
                                   .replyTopic("gatling.responses")
                                   .send("#{id}",
                                         String.format("{\"messageId\": #{id}, \"size\": %d}", PAYLOAD_SIZE),
                                         headers, String.class, String.class)
                                   .check(substring("messageId=#{id}"))
            )
            .feed(Util.idFeeder);

    {
        System.out.println(
                "Setting up simulation with variable user load patterns for spikes and stability periods.");
        setUp(
                customLoad.injectOpen(
                        constantUsersPerSec(CONSTANT_USERS).during(Duration.ofSeconds(CONSTANT_DURATIONS)),
                        rampUsersPerSec(CONSTANT_USERS).to(PEEK_USERS).during(Duration.ofSeconds(PEEK_RAMP_TIME)),
                        constantUsersPerSec(PEEK_USERS).during(Duration.ofSeconds(PEEK_DURATION)),
                        rampUsersPerSec(PEEK_USERS).to(CONSTANT_USERS).during(Duration.ofSeconds(PEEK_RAMP_TIME)),
                        constantUsersPerSec(CONSTANT_USERS).during(Duration.ofSeconds(CONSTANT_DURATIONS))
                )
        ).protocols(kafkaProtocol);
    }
}

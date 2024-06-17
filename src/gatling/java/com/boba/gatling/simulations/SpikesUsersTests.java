package com.boba.gatling.simulations;

import com.boba.gatling.utils.Util;
import com.rabbitmq.client.BuiltinExchangeType;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpExchange;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpProtocolBuilder;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpQueue;

import java.util.Map;

import static com.boba.gatling.simulations.RaisingUsersTests.parseEnvVar;
import static io.gatling.javaapi.core.CoreDsl.*;
import static ru.tinkoff.gatling.amqp.javaapi.AmqpDsl.amqp;
import static ru.tinkoff.gatling.amqp.javaapi.AmqpDsl.rabbitmq;

public class SpikesUsersTests extends Simulation {

    private static final int CONSTANT_USERS = parseEnvVar("CONSTANT_USERS", 10);
    private static final int CONSTANT_DURATIONS = parseEnvVar("DURATION", 10);
    private static final int PAYLOAD_SIZE = parseEnvVar("PAYLOAD_SIZE", 10);
    private static final int PEEK_DURATION = parseEnvVar("PEEK_DURATION", 20);
    private static final int PEEK_RAMP_TIME = parseEnvVar("PEEK_RAMP_TIME", 2);
    private static final int PEEK_USERS = parseEnvVar("PEEK_USERS", 5 * CONSTANT_USERS);

    private AmqpExchange topicExchange = new AmqpExchange("gatling", BuiltinExchangeType.TOPIC, true, false, Map.of());
    private AmqpQueue requestsQueue = new AmqpQueue("gatling.requests", true, false, false, Map.of());
    private AmqpQueue responsesQueue = new AmqpQueue("gatling.responses", true, false, false, Map.of());

    public AmqpProtocolBuilder amqpConf = amqp()
            .connectionFactory(
                    rabbitmq()
                            .host("rabbitmq.default.svc.cluster.local")
                            .port(5672)
                            .username("admin")
                            .password("admin")
                            .vhost("/")
                            .build()
            )
            .replyTimeout(600L)
            .consumerThreadsCount(8)
            .matchByMessageId()
            .usePersistentDeliveryMode()
            .declare(topicExchange)
            .declare(requestsQueue)
            .declare(responsesQueue)
            .bindQueue(responsesQueue, topicExchange, "responses", Map.of());

    ScenarioBuilder customLoad = scenario("Custom Load Pattern")
            .feed(Util.idFeeder)
            .exec(
                    amqp("Request Reply exchange test")
                            .requestReply()
                            .topicExchange("gatling", "request")
                            .replyExchange("gatling.responses")
                            .textMessage("{\"size\": " + PAYLOAD_SIZE + "}")
                            .messageId("#{id}")
                            .priority(0)
                            .contentType("application/json")
                            .headers(Map.of("test", "performance", "extra-test", "34-#{id}"))
                            .check(
                                    substring("messageId=#{id}")
                            )
            );

    {
        setUp(
                customLoad.injectOpen(
                        constantUsersPerSec(CONSTANT_USERS).during(CONSTANT_DURATIONS),
                        rampUsersPerSec(CONSTANT_USERS).to(PEEK_USERS).during(PEEK_RAMP_TIME),
                        constantUsersPerSec(PEEK_USERS).during(PEEK_DURATION),
                        rampUsersPerSec(PEEK_USERS).to(CONSTANT_USERS).during(PEEK_RAMP_TIME),
                        constantUsersPerSec(CONSTANT_USERS).during(CONSTANT_DURATIONS)
                )
        ).protocols(amqpConf);
    }
}

package com.boba.gatling.simulations;

import com.boba.gatling.utils.Util;
import com.rabbitmq.client.BuiltinExchangeType;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpExchange;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpProtocolBuilder;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpQueue;

import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static ru.tinkoff.gatling.amqp.javaapi.AmqpDsl.amqp;
import static ru.tinkoff.gatling.amqp.javaapi.AmqpDsl.rabbitmq;

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
            .replyTimeout(60000L)
            .consumerThreadsCount(8)
            .matchByMessageId()
            .usePersistentDeliveryMode()
            .declare(topicExchange)
            .declare(requestsQueue)
            .declare(responsesQueue)
            .bindQueue(responsesQueue, topicExchange, "responses", Map.of());

    ScenarioBuilder constantUsers = scenario("Constant users")
            .feed(Util.idFeeder)
            .exec(
                    amqp("Request Reply exchange test")
                            .requestReply()
                            .topicExchange("gatling", "request")
                            .replyExchange("gatling.responses")
                            .textMessage(String.valueOf(PAYLOAD_SIZE))
                            .messageId("#{id}")
                            .priority(0)
                            .contentType("application/json")
                            .headers(Map.of("test", "performance", "extra-test", "34-#{id}"))
                            .check(
                                    substring("messageId=#{id}")
                            )
            );

    {
        System.out.println("Starting test with " + END_USERS + " users for " + DURATION + " seconds");
        setUp(
                constantUsers.injectOpen(constantUsersPerSec(END_USERS).during(DURATION))
        ).protocols(amqpConf);
    }
}

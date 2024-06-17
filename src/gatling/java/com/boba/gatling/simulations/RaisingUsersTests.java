package com.boba.gatling.simulations;

import com.boba.gatling.utils.Util;
import com.rabbitmq.client.BuiltinExchangeType;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpExchange;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpProtocolBuilder;
import ru.tinkoff.gatling.amqp.javaapi.protocol.AmqpQueue;

import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static ru.tinkoff.gatling.amqp.javaapi.AmqpDsl.*;

public class RaisingUsersTests extends Simulation {

    private static final int START_RAMP_USERS = 0;
    private static final int END_RAMP_USERS = parseEnvVar("RAMP_USERS", 1);
    private static final int RAMP_DURATION = parseEnvVar("DURATION", 10);
    private static final int CONSTANT_DURATION = parseEnvVar("CONSTANT_DURATION", 10);

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

    ChainBuilder load = exec(http("rest").get("/rest")
                                         .check(status().is(200)));

    private AmqpExchange topic = new AmqpExchange("gatling", BuiltinExchangeType.TOPIC, true, false, Map.of());
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
            .declare(topic)
            .declare(requestsQueue)
            .declare(responsesQueue)
            .bindQueue(responsesQueue, topic, "responses", Map.of());

    ScenarioBuilder risingUsers = scenario("Raising users").exec(load);

    public ScenarioBuilder scn = scenario("AMQP test")
            .feed(Util.idFeeder)
            .exec(
                    amqp("Request Reply exchange test")
                            .requestReply()
                            .topicExchange("gatling", "request")
                            .replyExchange("gatling.responses")
                            .textMessage("{\"msg\": \"Hello message - #{id}\"}")
                            .messageId("#{id}")
                            .priority(0)
                            .contentType("application/json")
                            .headers(Map.of("test", "performance", "extra-test", "34-#{id}"))
                            .check(
                                    substring("messageId=#{id}")
                            )
            );


    {
        System.out.println(
                "Setting up simulation with " + START_RAMP_USERS + " to " + END_RAMP_USERS + " users in " + RAMP_DURATION + " seconds");
        setUp(scn.injectOpen(rampUsersPerSec(START_RAMP_USERS).to(END_RAMP_USERS)
                                                                        .during(RAMP_DURATION),
                                       constantUsersPerSec(END_RAMP_USERS).during(CONSTANT_DURATION))
        ).protocols(amqpConf);
    }
}

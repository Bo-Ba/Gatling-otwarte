package com.boba.gatling.simulations;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static com.boba.gatling.simulations.RaisingUsersTests.parseEnvVar;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class ConstantUsersTests extends Simulation {

    private static final int END_USERS = parseEnvVar("RAMP_USERS", 10);
    private static final int DURATION = parseEnvVar("DURATION", 60);
    private static final int PAYLOAD_SIZE = parseEnvVar("PAYLOAD_SIZE", 10);

    private final String endpoint =  "/rest/" + PAYLOAD_SIZE;

    ChainBuilder load = exec(http("rest").get(endpoint)
                                         .check(status().is(200)));


    HttpProtocolBuilder httpProtocol =
            http.baseUrl("http://microservice1.default.svc.cluster.local:1025")
                .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .acceptLanguageHeader("en-US,en;q=0.5")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/119.0"
                );

    ScenarioBuilder stressUsers = scenario("Stress users").exec(load);

    {
        System.out.println(
                "Setting up simulation with " + END_USERS + " users for " + DURATION + " seconds");
        setUp(
                stressUsers.injectOpen(constantUsersPerSec(END_USERS).during(DURATION))
        ).protocols(httpProtocol);
    }
}

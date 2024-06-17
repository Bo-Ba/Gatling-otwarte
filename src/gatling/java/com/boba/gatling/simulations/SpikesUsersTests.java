package com.boba.gatling.simulations;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static com.boba.gatling.simulations.RaisingUsersTests.parseEnvVar;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;


public class SpikesUsersTests extends Simulation {

    private static final int CONSTANT_USERS = parseEnvVar("CONSTANT_USERS", 10);
    private static final int CONSTANT_DURATIONS = parseEnvVar("DURATION", 10);
    private static final int PAYLOAD_SIZE = parseEnvVar("PAYLOAD_SIZE", 10);
    private static final int PEEK_DURATION = parseEnvVar("PEEK_DURATION", 20);
    private static final int PEEK_RAMP_TIME = parseEnvVar("PEEK_RAMP_TIME", 2);
    private static final int PEEK_USERS = parseEnvVar("PEEK_USERS", 5 * CONSTANT_USERS);

    private final String endpoint = "/rest/" + PAYLOAD_SIZE;

    ChainBuilder load = exec(http("Request").get(endpoint)
                                            .check(status().is(200)));

    HttpProtocolBuilder httpProtocol =
            http.baseUrl("http://localhost:1025")
                .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .acceptLanguageHeader("en-US,en;q=0.5")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/119.0");

    ScenarioBuilder customLoad = scenario("Custom Load Pattern").exec(load);

    {
        setUp(
                customLoad.injectOpen(
                        constantUsersPerSec(CONSTANT_USERS).during(CONSTANT_DURATIONS),
                        rampUsersPerSec(CONSTANT_USERS).to(PEEK_USERS).during(PEEK_RAMP_TIME),
                        constantUsersPerSec(PEEK_USERS).during(PEEK_DURATION),
                        rampUsersPerSec(PEEK_USERS).to(CONSTANT_USERS).during(PEEK_RAMP_TIME),
                        constantUsersPerSec(CONSTANT_USERS).during(CONSTANT_DURATIONS)
                )
        ).protocols(httpProtocol);
    }
}

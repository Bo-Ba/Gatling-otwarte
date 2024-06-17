package com.boba.gatling.simulations;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

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

    private final String endpoint =  "/rest/" + PAYLOAD_SIZE;

    ChainBuilder load = exec(http("rest").get(endpoint)
                                         .check(status().is(200)));


    HttpProtocolBuilder httpProtocol =
            http.baseUrl("http://localhost:1025")
                .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .acceptLanguageHeader("en-US,en;q=0.5")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/119.0"
                );

    ScenarioBuilder risingUsers = scenario("Raising users").exec(load);

    {
        System.out.println(
                "Setting up simulation with " + START_RAMP_USERS + " to " + END_RAMP_USERS + " users in " + RAMP_DURATION + " seconds");
        setUp(risingUsers.injectOpen(rampUsersPerSec(START_RAMP_USERS).to(END_RAMP_USERS)
                                                                        .during(RAMP_DURATION),
                                       constantUsersPerSec(END_RAMP_USERS).during(CONSTANT_DURATION))
        ).protocols(httpProtocol);
    }
}

package com.redis.enterprise;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpResponseException;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class DatabaseReadinessTests {

	private HttpServer server;
	private final AtomicInteger availabilityCalls = new AtomicInteger();
	private final AtomicInteger commandCalls = new AtomicInteger();
	private int unavailableResponses;
	private int availabilityStatus = 200;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/v1/", this::respond);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void createsDatabaseWhenManagementCommandsAreForbidden() throws Exception {
		try (Admin admin = admin()) {
			Database database = admin.createDatabase(Database.builder().build());
			Assertions.assertEquals(42L, database.getUid());
			Assertions.assertEquals(1, availabilityCalls.get());
			Assertions.assertEquals(0, commandCalls.get());
		}
	}

	@Test
	void waitsUntilDatabaseIsAvailable() throws Exception {
		unavailableResponses = 2;
		try (Admin admin = admin()) {
			admin.createDatabase(Database.builder().build());
			Assertions.assertEquals(3, availabilityCalls.get());
			Assertions.assertEquals(0, commandCalls.get());
		}
	}

	@Test
	void timesOutWhenDatabaseRemainsUnavailable() throws Exception {
		availabilityStatus = 503;
		try (Admin admin = admin()) {
			admin.withDatabaseCreationTimeout(Duration.ofMillis(200));
			Assertions.assertThrows(ConditionTimeoutException.class,
					() -> admin.createDatabase(Database.builder().build()));
			Assertions.assertTrue(availabilityCalls.get() > 0);
			Assertions.assertEquals(0, commandCalls.get());
		}
	}

	@Test
	void propagatesForbiddenAvailabilityResponse() throws Exception {
		availabilityStatus = 403;
		try (Admin admin = admin()) {
			HttpResponseException exception = Assertions.assertThrows(HttpResponseException.class,
					() -> admin.createDatabase(Database.builder().build()));
			Assertions.assertEquals(403, exception.getStatusCode());
			Assertions.assertEquals(1, availabilityCalls.get());
		}
	}

	private Admin admin() {
		return new Admin().withProtocol("http").withHost("localhost").withPort(server.getAddress().getPort())
				.withDatabaseCreationPollInterval(Duration.ofMillis(10))
				.withDatabaseCreationTimeout(Duration.ofSeconds(5));
	}

	private void respond(HttpExchange exchange) throws IOException {
		try {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/v1/modules")) {
				respond(exchange, 200, "[]");
			} else if (path.equals("/v1/bdbs") && exchange.getRequestMethod().equals("POST")) {
				respond(exchange, 200, "{\"uid\":42,\"type\":\"redis\",\"port\":12000}");
			} else if (path.equals("/v1/bdbs/42/availability") && exchange.getRequestMethod().equals("GET")) {
				int call = availabilityCalls.incrementAndGet();
				respond(exchange, call <= unavailableResponses ? 503 : availabilityStatus, "");
			} else if (path.equals("/v1/bdbs/42/command")) {
				commandCalls.incrementAndGet();
				respond(exchange, 403, "{\"error_code\":\"command_not_allowed\"}");
			} else {
				respond(exchange, 404, "{}");
			}
		} finally {
			exchange.close();
		}
	}

	private void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] content = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, content.length == 0 ? -1 : content.length);
		if (content.length > 0) {
			exchange.getResponseBody().write(content);
		}
	}
}

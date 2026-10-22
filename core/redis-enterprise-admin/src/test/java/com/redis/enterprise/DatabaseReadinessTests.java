package com.redis.enterprise;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpResponseException;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisContainer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Testcontainers
class DatabaseReadinessTests {

	@Container
	private static final RedisContainer REDIS = new RedisContainer(
			RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));

	private HttpServer server;
	private final AtomicInteger managementProbeCalls = new AtomicInteger();
	private int databasePort;
	private int creationStatus = 200;
	private boolean autoAssignPort;
	private String endpoints = "[]";

	@BeforeEach
	void startServer() throws IOException {
		databasePort = REDIS.getMappedPort(6379);
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/v1/", this::respond);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void pingsActualDatabaseWhenManagementProbesAreForbidden() throws Exception {
		try (Admin admin = admin()) {
			Database database = admin.createDatabase(Database.builder().build());
			Assertions.assertEquals(42L, database.getUid());
			Assertions.assertEquals(databasePort, database.getPort());
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	@Test
	void usesAssignedDatabasePort() throws Exception {
		autoAssignPort = true;
		try (Admin admin = admin()) {
			admin.createDatabase(Database.builder().build());
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	@Test
	void readsCompletePongResponse() throws Exception {
		try (ServerSocket endpoint = new ServerSocket(0); Admin admin = admin()) {
			databasePort = endpoint.getLocalPort();
			CompletableFuture<Void> response = replyToPing(endpoint, "+PONG\r\n");
			admin.createDatabase(Database.builder().build());
			response.get(5, TimeUnit.SECONDS);
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	@Test
	void retriesUntilDatabaseRepliesPong() throws Exception {
		try (ServerSocket endpoint = new ServerSocket(0); Admin admin = admin()) {
			databasePort = endpoint.getLocalPort();
			CompletableFuture<Void> response = replyToPing(endpoint, "-BUSY\r\n", "+PONG\r\n");
			admin.createDatabase(Database.builder().build());
			response.get(5, TimeUnit.SECONDS);
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	@Test
	void usesAdvertisedEndpointWhenAdminHostDoesNotServeDatabase() throws Exception {
		try (ServerSocket endpoint = new ServerSocket(0, 50, InetAddress.getByName("::1"));
				Admin admin = admin().withHost("127.0.0.1")) {
			databasePort = endpoint.getLocalPort();
			endpoints = "[{\"addr\":[\"::1\"],\"port\":" + databasePort + "}]";
			CompletableFuture<Void> response = replyToPing(endpoint, "+PONG\r\n");
			admin.createDatabase(Database.builder().build());
			response.get(5, TimeUnit.SECONDS);
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	@Test
	void rejectsTcpEndpointThatDoesNotReply() throws Exception {
		try (ServerSocket endpoint = new ServerSocket(0); Admin admin = admin()) {
			databasePort = endpoint.getLocalPort();
			admin.withDatabaseCreationTimeout(Duration.ofMillis(200));
			Assertions.assertThrows(ConditionTimeoutException.class,
					() -> admin.createDatabase(Database.builder().build()));
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	@Test
	void rejectsNonPongResponse() throws Exception {
		try (ServerSocket endpoint = new ServerSocket(0); Admin admin = admin()) {
			databasePort = endpoint.getLocalPort();
			admin.withDatabaseCreationTimeout(Duration.ofMillis(300));
			CompletableFuture<Void> response = replyToPing(endpoint, "+OK\r\n");
			Assertions.assertThrows(ConditionTimeoutException.class,
					() -> admin.createDatabase(Database.builder().build()));
			response.get(5, TimeUnit.SECONDS);
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	@Test
	void propagatesDatabaseCreationFailure() throws Exception {
		creationStatus = 403;
		try (Admin admin = admin()) {
			HttpResponseException exception = Assertions.assertThrows(HttpResponseException.class,
					() -> admin.createDatabase(Database.builder().build()));
			Assertions.assertEquals(403, exception.getStatusCode());
			Assertions.assertEquals(0, managementProbeCalls.get());
		}
	}

	private CompletableFuture<Void> replyToPing(ServerSocket endpoint, String... replies) throws IOException {
		endpoint.setSoTimeout(2000);
		return CompletableFuture.runAsync(() -> {
			try {
				for (String reply : replies) {
					try (Socket connection = endpoint.accept()) {
						connection.setSoTimeout(2000);
						byte[] expected = "*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII);
						Assertions.assertArrayEquals(expected, connection.getInputStream().readNBytes(expected.length));
						for (byte value : reply.getBytes(StandardCharsets.US_ASCII)) {
							connection.getOutputStream().write(value);
							connection.getOutputStream().flush();
						}
					}
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
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
				respond(exchange, creationStatus,
						"{\"uid\":42,\"type\":\"redis\",\"port\":" + (autoAssignPort ? 0 : databasePort) + "}");
			} else if (path.equals("/v1/bdbs/42") && exchange.getRequestMethod().equals("GET")) {
				respond(exchange, 200, "{\"uid\":42,\"type\":\"redis\",\"port\":" + databasePort
						+ ",\"endpoints\":" + endpoints + "}");
			} else {
				managementProbeCalls.incrementAndGet();
				respond(exchange, 403, "{\"error_code\":\"command_not_allowed\"}");
			}
		} finally {
			exchange.close();
		}
	}

	private void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] content = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, content.length);
		exchange.getResponseBody().write(content);
	}
}

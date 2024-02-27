package com.redis.enterprise;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ParseException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.util.unit.DataSize;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;

@TestInstance(Lifecycle.PER_CLASS)
abstract class AbstractTestBase {

	private static final Logger log = Logger.getLogger(AbstractTestBase.class.getName());

	protected Admin admin;

	@BeforeAll
	void setupAdmin() throws ParseException, GeneralSecurityException, IOException {
		admin = admin();
	}

	protected abstract Admin admin();

	@AfterAll
	void teardownAdmin() throws Exception {
		admin.close();
	}

	@BeforeEach
	void deleteAllDatabases() throws GeneralSecurityException, IOException, ParseException {
		List<Database> databases = admin.getDatabases();
		log.log(Level.INFO, "Deleting databases {0}",
				databases.stream().map(Database::getUid).collect(Collectors.toList()));
		for (Database database : databases) {
			admin.deleteDatabase(database.getUid());
		}
		Awaitility.await().until(() -> admin.getDatabases().isEmpty());
	}

	@Test
	void createDatabase() throws ParseException, GeneralSecurityException, IOException {
		String databaseName = "CreateDBTest";
		admin.createDatabase(Database.builder().name(databaseName).build());
		Stream<Database> stream = admin.getDatabases().stream().filter(d -> d.getName().equals(databaseName));
		Assertions.assertEquals(1, stream.count());
	}

	@Test
	void createClusterDatabase() throws ParseException, GeneralSecurityException, IOException {
		String databaseName = "CreateClusterDBTest";
		admin.createDatabase(Database.builder().name(databaseName).ossCluster(true).port(12000).build());
		List<Database> databases = admin.getDatabases();
		Assertions.assertEquals(1, databases.size());
		Assertions.assertEquals(databaseName, databases.get(0).getName());
		Database database = databases.get(0);
		RedisClusterClient client = RedisClusterClient.create(RedisURI.create(admin.getHost(), database.getPort()));
		try (StatefulRedisClusterConnection<String, String> connection = client.connect()) {
			Assertions.assertEquals("PONG", connection.sync().ping());
		}
		client.shutdown();
		client.getResources().shutdown();
	}

	@Test
	void createSearchDatabase() throws ParseException, IOException, GeneralSecurityException {
		String databaseName = "CreateSearchDBTest";
		admin.createDatabase(Database.builder().name(databaseName).module(RedisModule.SEARCH).build());
		List<Database> databases = admin.getDatabases();
		Assertions.assertEquals(1, databases.size());
		Assertions.assertEquals(RedisModule.SEARCH.getModuleName(), databases.get(0).getModules().get(0).getName());
	}

	@Test
	void deleteDatabase() throws ParseException, GeneralSecurityException, IOException {
		String databaseName = "DeleteDBTest";
		Database database = admin.createDatabase(Database.builder().name(databaseName).build());
		admin.deleteDatabase(database.getUid());
		Awaitility.await().until(() -> admin.getDatabases().stream().noneMatch(d -> d.getUid() == database.getUid()));
	}

	@Test
	void createDatabaseException() throws ParseException, IOException {
		Assertions.assertThrows(HttpResponseException.class, () -> admin.createDatabase(
				Database.builder().name("DatabaseCreateExceptionTestDB").memory(DataSize.ofGigabytes(10)).build()));
	}

}
package com.redis.enterprise;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.core5.http.ParseException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.enterprise.rest.Database;
import com.redis.testcontainers.RedisEnterpriseContainer;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class AdminTests {

	private static final Logger log = LoggerFactory.getLogger(AdminTests.class);

	@Container
	private static RedisEnterpriseContainer server = new RedisEnterpriseContainer();

	private static Admin admin;

	@BeforeAll
	static void setupAdmin() throws ParseException, GeneralSecurityException, IOException {
		admin = new Admin(RedisEnterpriseContainer.ADMIN_USERNAME,
				RedisEnterpriseContainer.ADMIN_PASSWORD.toCharArray());
		admin.setHost(server.getHost());
	}

	@AfterAll
	static void teardownAdmin() throws Exception {
		admin.close();
	}

	@BeforeEach
	void deleteAllDatabases() throws GeneralSecurityException, IOException, ParseException {
		List<Database> databases = admin.getDatabases();
		log.info("Deleting databases {}", databases.stream().map(Database::getUid).collect(Collectors.toList()));
		for (Database database : databases) {
			admin.deleteDatabase(database.getUid());
		}
		Awaitility.await().until(() -> admin.getDatabases().isEmpty());
	}

	@Test
	void createDatabase() throws ParseException, GeneralSecurityException, IOException {
		String databaseName = "CreateTestDB";
		Database request = new Database();
		request.setName(databaseName);
		request.setOssCluster(true);
		admin.createDatabase(request);
		Stream<Database> stream = admin.getDatabases().stream().filter(d -> d.getName().equals(databaseName));
		Assertions.assertEquals(1, stream.count());
	}

	@Test
	void deleteDatabase() throws ParseException, GeneralSecurityException, IOException {
		String databaseName = "DeleteTestDB";
		Database request = new Database();
		request.setName(databaseName);
		Database database = admin.createDatabase(request);
		admin.deleteDatabase(database.getUid());
		Awaitility.await().until(() -> admin.getDatabases().stream().noneMatch(d -> d.getUid() == database.getUid()));
	}

}
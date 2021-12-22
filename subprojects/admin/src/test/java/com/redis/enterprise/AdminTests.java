package com.redis.enterprise;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.enterprise.rest.Database;
import com.redis.enterprise.rest.Database.Builder.Module;
import com.redis.enterprise.rest.ModuleInstallResponse;
import com.redis.testcontainers.RedisEnterpriseContainer;
import com.redis.testcontainers.RedisEnterpriseContainer.RedisModule;

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
		String databaseName = "CreateDBTest";
		admin.createDatabase(Database.name(databaseName).ossCluster(true).build());
		Stream<Database> stream = admin.getDatabases().stream().filter(d -> d.getName().equals(databaseName));
		Assertions.assertEquals(1, stream.count());
	}

	@Test
	void createSearchDatabase() throws ParseException, IOException {
		String databaseName = "CreateSearchDBTest";
		admin.createDatabase(Database.name(databaseName).module(Module.SEARCH).build());
		List<Database> databases = admin.getDatabases();
		Assertions.assertEquals(1, databases.size());
		Assertions.assertEquals(Module.SEARCH.getName(), databases.get(0).getModules().get(0).getName());
	}

	@Test
	void deleteDatabase() throws ParseException, GeneralSecurityException, IOException {
		String databaseName = "DeleteDBTest";
		Database database = admin.createDatabase(Database.name(databaseName).build());
		admin.deleteDatabase(database.getUid());
		Awaitility.await().until(() -> admin.getDatabases().stream().noneMatch(d -> d.getUid() == database.getUid()));
	}

	@Test
	void installModule() throws IOException, ParseException {
		String gearsModuleFile = "redisgears.linux-bionic-x64.1.0.6.zip";
		try (InputStream zipInputStream = getClass().getClassLoader().getResourceAsStream(gearsModuleFile)) {
			log.info("Installing module {}", gearsModuleFile);
			ModuleInstallResponse response = admin.installModule(gearsModuleFile, zipInputStream);
			log.info("Installed module {}, action ID: {}", gearsModuleFile, response.getActionUid());
			Assertions.assertTrue(
					admin.getModules().stream().anyMatch(m -> m.getName().equals(RedisModule.GEARS.getName())));
		}
		admin.createDatabase(Database.name("ModuleInstallDBTest").module(Module.SEARCH).build());
		List<Database> databases = admin.getDatabases();
		Assertions.assertEquals(1, databases.size());
		Assertions.assertEquals(Module.SEARCH.getName(), databases.get(0).getModules().get(0).getName());
	}

	@Test
	void createDatabaseException() throws ParseException, IOException {
		Assertions.assertThrows(HttpResponseException.class, () -> admin.createDatabase(
				Database.name("DatabaseCreateExceptionTestDB").memory(DataSize.ofGigabytes(10)).build()));
	}

}

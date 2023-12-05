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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisEnterpriseContainer;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
@EnabledOnOs(value = OS.LINUX)
class AdminTests {

    private static final Logger log = Logger.getLogger(AdminTests.class.getName());

    @Container
    private static RedisEnterpriseContainer server = new RedisEnterpriseContainer(
            RedisEnterpriseContainer.DEFAULT_IMAGE_NAME.withTag(RedisEnterpriseContainer.DEFAULT_TAG));

    private static Admin admin;

    @BeforeAll
    static void setupAdmin() throws ParseException, GeneralSecurityException, IOException {
        admin = new Admin(RedisEnterpriseContainer.ADMIN_USERNAME, RedisEnterpriseContainer.ADMIN_PASSWORD.toCharArray());
        admin.setHost(server.getHost());
    }

    @AfterAll
    static void teardownAdmin() throws Exception {
        admin.close();
    }

    @BeforeEach
    void deleteAllDatabases() throws GeneralSecurityException, IOException, ParseException {
        List<Database> databases = admin.getDatabases();
        log.log(Level.INFO, "Deleting databases {0}", databases.stream().map(Database::getUid).collect(Collectors.toList()));
        for (Database database : databases) {
            admin.deleteDatabase(database.getUid());
        }
        Awaitility.await().until(() -> admin.getDatabases().isEmpty());
    }

    @Test
    void createDatabase() throws ParseException, GeneralSecurityException, IOException {
        String databaseName = "CreateDBTest";
        admin.createDatabase(Database.name(databaseName).build());
        Stream<Database> stream = admin.getDatabases().stream().filter(d -> d.getName().equals(databaseName));
        Assertions.assertEquals(1, stream.count());
    }

    @Test
    void createClusterDatabase() throws ParseException, GeneralSecurityException, IOException {
        String databaseName = "CreateClusterDBTest";
        admin.createDatabase(
                Database.name(databaseName).ossCluster(true).port(RedisEnterpriseContainer.DEFAULT_DATABASE_PORT).build());
        List<Database> databases = admin.getDatabases();
        Assertions.assertEquals(1, databases.size());
        Assertions.assertEquals(databaseName, databases.get(0).getName());
        Database database = databases.get(0);
        RedisClusterClient client = RedisClusterClient.create(RedisURI.create(server.getHost(), database.getPort()));
        try (StatefulRedisClusterConnection<String, String> connection = client.connect()) {
            Assertions.assertEquals("PONG", connection.sync().ping());
        }
        client.shutdown();
        client.getResources().shutdown();
    }

    @Test
    void createSearchDatabase() throws ParseException, IOException {
        String databaseName = "CreateSearchDBTest";
        admin.createDatabase(Database.name(databaseName).module(RedisModule.SEARCH).build());
        List<Database> databases = admin.getDatabases();
        Assertions.assertEquals(1, databases.size());
        Assertions.assertEquals(RedisModule.SEARCH.getModuleName(), databases.get(0).getModules().get(0).getName());
    }

    @Test
    void deleteDatabase() throws ParseException, GeneralSecurityException, IOException {
        String databaseName = "DeleteDBTest";
        Database database = admin.createDatabase(Database.name(databaseName).build());
        admin.deleteDatabase(database.getUid());
        Awaitility.await().until(() -> admin.getDatabases().stream().noneMatch(d -> d.getUid() == database.getUid()));
    }

    @Test
    void createDatabaseException() throws ParseException, IOException {
        Assertions.assertThrows(HttpResponseException.class, () -> admin
                .createDatabase(Database.name("DatabaseCreateExceptionTestDB").memory(DataSize.ofGigabytes(10)).build()));
    }

}

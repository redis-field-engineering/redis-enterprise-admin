package com.redis.testcontainers.test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.apache.hc.core5.http.ParseException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.enterprise.Admin;
import com.redis.enterprise.rest.Database;
import com.redis.testcontainers.RedisEnterpriseContainer;

@Testcontainers
class AdminTests {

	@Container
	private static RedisEnterpriseContainer server = new RedisEnterpriseContainer();

	private static Admin admin;

	@BeforeAll
	static void setupContainer() throws ParseException, GeneralSecurityException, IOException {
		admin = new Admin(RedisEnterpriseContainer.ADMIN_USERNAME,
				RedisEnterpriseContainer.ADMIN_PASSWORD.toCharArray());
		admin.setHost(server.getHost());
		admin.deleteDatabase(admin.getDatabases().get(0).getUid());
		Awaitility.await().until(() -> admin.getDatabases().isEmpty());
	}

	@Test
	void createDatabase() throws ParseException, GeneralSecurityException, IOException {
		String databaseName = "testdb";
		Database request = new Database();
		request.setName(databaseName);
		request.setOssCluster(true);
		admin.createDatabase(request);
		List<Database> databases = admin.getDatabases();
		Assertions.assertEquals(1, databases.size());
		Assertions.assertEquals(databaseName, databases.get(0).getName());
	}

}

package com.redis.enterprise;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.redis.enterprise.testcontainers.RedisEnterpriseServer;

@EnabledIfEnvironmentVariable(named = RedisEnterpriseServer.ENV_HOST, matches = ".*")
class ServerAdminTests extends AbstractAdminTests {

	@Override
	protected Admin admin() {
		Admin admin = new Admin();
		String user = System.getenv(RedisEnterpriseServer.ENV_USER);
		if (hasLength(user)) {
			admin.withUserName(user);
		}
		String password = System.getenv(RedisEnterpriseServer.ENV_PASSWORD);
		if (hasLength(password)) {
			admin.withPassword(password);
		}
		admin.withHost(System.getenv(RedisEnterpriseServer.ENV_HOST));
		return admin;
	}

	private static boolean hasLength(String string) {
		return string != null && string.length() > 0;
	}

}

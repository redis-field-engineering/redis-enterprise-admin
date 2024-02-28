package com.redis.enterprise;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.redis.testcontainers.RedisEnterpriseServer;

@EnabledIfEnvironmentVariable(named = RedisEnterpriseServer.ENV_HOST, matches = ".*")
class ServerAdminTests extends AbstractAdminTests {

	@Override
	protected Admin admin() {
		Admin admin = new Admin();
		admin.withHost(System.getenv(RedisEnterpriseServer.ENV_HOST));
		return admin;
	}

}

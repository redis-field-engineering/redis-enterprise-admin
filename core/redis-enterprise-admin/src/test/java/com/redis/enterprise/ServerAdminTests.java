package com.redis.enterprise;

import org.junit.jupiter.api.Disabled;

@Disabled // @EnabledIfEnvironmentVariable(named = RedisEnterpriseServer.ENV_HOST, matches
			// = ".*")
class ServerAdminTests extends AbstractAdminTests {

	@Override
	protected Admin admin() {
		Admin admin = new Admin();
		// TODO admin.withHost(System.getenv(RedisEnterpriseServer.ENV_HOST));
		return admin;
	}

}

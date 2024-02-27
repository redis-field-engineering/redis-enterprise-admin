package com.redis.enterprise;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(value = OS.MAC)
class RedisEnterpriseServerTests extends AbstractTestBase {

	@Override
	protected Admin admin() {
		Admin admin = new Admin();
		admin.withHost("nuc");
		return admin;
	}

}

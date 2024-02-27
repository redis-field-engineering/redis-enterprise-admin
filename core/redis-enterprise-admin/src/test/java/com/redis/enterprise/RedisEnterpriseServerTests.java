package com.redis.enterprise;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class RedisEnterpriseServerTests extends AbstractTestBase {

	@Override
	protected Admin admin() {
		return new Admin();
	}

}

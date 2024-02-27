package com.redis.enterprise;

import org.junit.jupiter.api.Disabled;

@Disabled
class RedisEnterpriseServerTests extends AbstractTestBase {

	@Override
	protected Admin admin() {
		return new Admin();
	}

}

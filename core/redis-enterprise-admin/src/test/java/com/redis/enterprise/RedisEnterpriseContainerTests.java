package com.redis.enterprise;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisEnterpriseContainer;

@Testcontainers
@EnabledOnOs(value = OS.LINUX)
class RedisEnterpriseContainerTests extends AbstractTestBase {

	@Container
	private static RedisEnterpriseContainer server = new RedisEnterpriseContainer(
			RedisEnterpriseContainer.DEFAULT_IMAGE_NAME.withTag(RedisEnterpriseContainer.DEFAULT_TAG));

	@Override
	protected Admin admin() {
		Admin admin = new Admin();
		admin.withHost(server.getHost());
		return admin;
	}

}

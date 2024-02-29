package com.redis.enterprise;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledOnOs(value = OS.LINUX)
class ContainerAdminTests extends AbstractAdminTests {

	@Container
	private static TestRedisEnterpriseContainer container = new TestRedisEnterpriseContainer(
			TestRedisEnterpriseContainer.DEFAULT_IMAGE_NAME.withTag(TestRedisEnterpriseContainer.DEFAULT_TAG));

	@Override
	protected Admin admin() {
		Admin admin = new Admin();
		admin.withHost(container.getHost());
		return admin;
	}

}

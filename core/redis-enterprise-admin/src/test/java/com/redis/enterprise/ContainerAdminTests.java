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
		// These Linux-only tests create databases with automatically assigned ports.
		// Reach the container directly: only the fixed 12000 port is forwarded to the host.
		admin.withHost(container.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next()
				.getIpAddress());
		return admin;
	}

}

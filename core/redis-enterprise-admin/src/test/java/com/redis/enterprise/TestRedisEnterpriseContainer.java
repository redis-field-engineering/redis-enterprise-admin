package com.redis.enterprise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.AbstractRedisEnterpriseContainer;

public class TestRedisEnterpriseContainer extends AbstractRedisEnterpriseContainer<TestRedisEnterpriseContainer> {

	private final Admin admin = new Admin();
	private Database database = Database.builder().shardCount(2).port(12000).ossCluster(true)
			.modules(RedisModule.SEARCH, RedisModule.JSON, RedisModule.TIMESERIES, RedisModule.BLOOM).build();

	private final Logger log = LoggerFactory.getLogger(TestRedisEnterpriseContainer.class);

	public TestRedisEnterpriseContainer(String dockerImageName) {
		super(dockerImageName);
	}

	public TestRedisEnterpriseContainer(DockerImageName dockerImageName) {
		super(dockerImageName);
	}

	@Override
	protected String getAdminUserName() {
		return admin.getUserName();
	}

	@Override
	protected String getAdminPassword() {
		return admin.getPassword();
	}

	@Override
	public int getRedisPort() {
		return database.getPort();
	}

	@Override
	public boolean isRedisCluster() {
		return database.isOssCluster();
	}

	@Override
	protected void doStart() {
		admin.withHost(getHost());
		addFixedExposedPort(admin.getPort(), admin.getPort());
		addFixedExposedPort(database.getPort(), database.getPort());
		super.doStart();
	}

	@Override
	protected void createCluster() throws Exception {
		log.info("Waiting for cluster bootstrap");
		admin.waitForBoostrap();
		super.createCluster();
		Database response = admin.createDatabase(database);
		log.info("Created database {} with UID {}", response.getName(), response.getUid());
	}

}

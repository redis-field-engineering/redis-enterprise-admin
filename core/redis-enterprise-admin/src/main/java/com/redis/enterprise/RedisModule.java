package com.redis.enterprise;

public enum RedisModule {

	PROBABILISTIC("bf"), JSON("ReJSON"), SEARCH("search"), TIMESERIES("timeseries");

	private final String moduleName;

	RedisModule(String name) {
		this.moduleName = name;
	}

	public String getModuleName() {
		return moduleName;
	}

}
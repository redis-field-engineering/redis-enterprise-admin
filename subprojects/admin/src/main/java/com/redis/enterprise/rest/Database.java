package com.redis.enterprise.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.hc.core5.util.Asserts;
import org.springframework.util.unit.DataSize;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Database {

	private Long uid;
	private String name;
	private boolean replication;
	private boolean sharding;
	private long memory;
	private Integer port;
	private String type;
	private boolean ossCluster;
	private ProxyPolicy proxyPolicy;
	private IPType ossClusterAPIPreferredIPType;
	private List<ShardKeyRegex> shardKeyRegex;
	private Integer shardCount;
	private ShardPlacement shardPlacement;
	private List<ModuleConfig> modules;

	private Database() {
	}

	private Database(Builder builder) {
		this.uid = builder.uid;
		this.name = builder.name;
		this.replication = builder.replication;
		this.sharding = builder.sharding;
		this.memory = builder.memory.toBytes();
		this.port = builder.port;
		this.type = builder.type;
		this.ossCluster = builder.ossCluster;
		this.proxyPolicy = builder.proxyPolicy;
		this.ossClusterAPIPreferredIPType = builder.ossClusterAPIPreferredIPType;
		this.shardKeyRegex = builder.shardKeyRegexes.stream().map(ShardKeyRegex::new).collect(Collectors.toList());
		this.shardCount = builder.shardCount;
		this.shardPlacement = builder.shardPlacement;
		this.modules = builder.moduleConfigs;
	}

	public Long getUid() {
		return uid;
	}

	public void setUid(long uid) {
		this.uid = uid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean getReplication() {
		return replication;
	}

	public void setReplication(boolean replication) {
		this.replication = replication;
	}

	public boolean getSharding() {
		return sharding;
	}

	public void setSharding(boolean sharding) {
		this.sharding = sharding;
	}

	@JsonProperty("memory_size")
	public long getMemory() {
		return memory;
	}

	public void setMemory(long memory) {
		this.memory = memory;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@JsonProperty("oss_cluster")
	public boolean getOssCluster() {
		return ossCluster;
	}

	public void setOssCluster(boolean ossCluster) {
		this.ossCluster = ossCluster;
	}

	@JsonProperty("proxy_policy")
	public ProxyPolicy getProxyPolicy() {
		return proxyPolicy;
	}

	public void setProxyPolicy(ProxyPolicy proxyPolicy) {
		this.proxyPolicy = proxyPolicy;
	}

	@JsonProperty("oss_cluster_api_preferred_ip_type")
	public IPType getOssClusterAPIPreferredIPType() {
		return ossClusterAPIPreferredIPType;
	}

	public void setOssClusterAPIPreferredIPType(IPType ossClusterAPIPreferredIPType) {
		this.ossClusterAPIPreferredIPType = ossClusterAPIPreferredIPType;
	}

	@JsonProperty("shard_key_regex")
	public List<ShardKeyRegex> getShardKeyRegex() {
		return shardKeyRegex;
	}

	public void setShardKeyRegex(List<ShardKeyRegex> shardKeyRegex) {
		this.shardKeyRegex = shardKeyRegex;
	}

	@JsonProperty("shards_count")
	public Integer getShardCount() {
		return shardCount;
	}

	public void setShardCount(int shardCount) {
		this.shardCount = shardCount;
	}

	@JsonProperty("shards_placement")
	public ShardPlacement getShardPlacement() {
		return shardPlacement;
	}

	public void setShardPlacement(ShardPlacement shardPlacement) {
		this.shardPlacement = shardPlacement;
	}

	@JsonProperty("module_list")
	public List<ModuleConfig> getModules() {
		return modules;
	}

	public void setModules(List<ModuleConfig> modules) {
		this.modules = modules;
	}

	public enum IPType {
		@JsonProperty("internal")
		INTERNAL, @JsonProperty("external")
		EXTERNAL
	}

	public enum ProxyPolicy {
		@JsonProperty("single")
		SINGLE, @JsonProperty("all-master-shards")
		ALL_MASTER_SHARDS, @JsonProperty("all-nodes")
		ALL_NODES
	}

	public enum ShardPlacement {

		@JsonProperty("dense")
		DENSE, @JsonProperty("sparse")
		SPARSE
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ModuleConfig {

		private String name;
		private String id;
		private String args = "";

		public ModuleConfig() {
		}

		public ModuleConfig(String name) {
			this.name = name;
		}

		@JsonProperty("module_name")
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@JsonProperty("module_id")
		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		@JsonProperty("module_args")
		public String getArgs() {
			return args;
		}

		public void setArgs(String args) {
			this.args = args;
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ShardKeyRegex {

		private String regex;

		public ShardKeyRegex() {
		}

		public ShardKeyRegex(String regex) {
			this.regex = regex;
		}

		public String getRegex() {
			return regex;
		}

		public void setRegex(String regex) {
			this.regex = regex;
		}

	}

	public static Builder name(String name) {
		return new Builder().name(name);
	}

	public static final class Builder {

		public static final long DEFAULT_MEMORY_MB = 100;
		public static final int DEFAULT_CLUSTER_SHARD_COUNT = 3;
		public static final String[] DEFAULT_SHARD_KEY_REGEXES = new String[] { ".*\\{(?<tag>.*)\\}.*", "(?<tag>.*)" };

		private Long uid;
		private String name;
		private boolean replication;
		private boolean sharding;
		private DataSize memory = DataSize.ofMegabytes(DEFAULT_MEMORY_MB);
		private Integer port;
		private String type;
		private boolean ossCluster;
		private ProxyPolicy proxyPolicy;
		private IPType ossClusterAPIPreferredIPType;
		private List<String> shardKeyRegexes = new ArrayList<>();
		private Integer shardCount;
		private ShardPlacement shardPlacement;
		private List<ModuleConfig> moduleConfigs = new ArrayList<>();

		private Builder() {
		}

		public Builder uid(Long uid) {
			this.uid = uid;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder replication(boolean replication) {
			this.replication = replication;
			return this;
		}

		public Builder sharding(boolean sharding) {
			this.sharding = sharding;
			return this;
		}

		public Builder memory(DataSize memory) {
			this.memory = memory;
			return this;
		}

		public Builder port(int port) {
			this.port = port;
			return this;
		}

		public Builder type(String type) {
			this.type = type;
			return this;
		}

		public Builder ossCluster(boolean ossCluster) {
			this.ossCluster = ossCluster;
			if (ossCluster) {
				proxyPolicy(ProxyPolicy.ALL_MASTER_SHARDS);
				ossClusterAPIPreferredIPType(IPType.EXTERNAL);
				if (shardCount == null || shardCount < 2) {
					shardCount(DEFAULT_CLUSTER_SHARD_COUNT);
				}
			}
			return this;
		}

		public Builder proxyPolicy(ProxyPolicy proxyPolicy) {
			this.proxyPolicy = proxyPolicy;
			return this;
		}

		public Builder ossClusterAPIPreferredIPType(IPType ossClusterAPIPreferredIPType) {
			this.ossClusterAPIPreferredIPType = ossClusterAPIPreferredIPType;
			return this;
		}

		public Builder shardKeyRegex(String regex) {
			this.shardKeyRegexes.add(regex);
			return this;
		}

		public Builder shardKeyRegexes(String... regexes) {
			for (String regex : regexes) {
				shardKeyRegex(regex);
			}
			return this;
		}

		public Builder shardCount(int shardCount) {
			Asserts.check(shardCount > 0, "Shard count must be strictly positive");
			this.shardCount = shardCount;
			if (shardCount > 1) {
				sharding(true);
				shardKeyRegexes(DEFAULT_SHARD_KEY_REGEXES);
			}
			return this;
		}

		public Builder shardPlacement(ShardPlacement shardPlacement) {
			this.shardPlacement = shardPlacement;
			return this;
		}

		public Builder module(Module module) {
			this.moduleConfigs.add(new ModuleConfig(module.getName()));
			return this;
		}

		public Builder modules(Module... modules) {
			for (Module module : modules) {
				module(module);
			}
			return this;
		}

		public Builder moduleConfig(ModuleConfig moduleConfig) {
			this.moduleConfigs.add(moduleConfig);
			return this;
		}

		public Builder moduleConfigs(ModuleConfig... moduleConfigs) {
			this.moduleConfigs = Arrays.asList(moduleConfigs);
			return this;
		}

		public Database build() {
			return new Database(this);
		}

		public enum Module {

			BLOOM("bf"), GEARS("rg"), GRAPH("graph"), JSON("ReJSON"), SEARCH("search"), TIMESERIES("timeseries");

			private final String name;

			Module(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

		}
	}
}

package com.redis.enterprise;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContexts;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.redis.enterprise.Database.ModuleConfig;
import com.redis.enterprise.rest.Bootstrap;
import com.redis.enterprise.rest.CommandResponse;
import com.redis.enterprise.rest.InstalledModule;

public class Admin implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(Admin.class);

	public static final String DEFAULT_USER_NAME = "admin@redis.com";
	public static final String DEFAULT_PASSWORD = "redis123";
	public static final String DEFAULT_PROTOCOL = "https";
	public static final String DEFAULT_HOST = "localhost";
	public static final int DEFAULT_PORT = 9443;

	private static final String BOOTSTRAP = "bootstrap";
	private static final String MODULES = "modules";
	private static final String BDBS = "bdbs";
	private static final String COMMAND = "command";
	private static final String CONTENT_TYPE_JSON = "application/json";
	private static final String V1 = "/v1/";
	private static final CharSequence PATH_SEPARATOR = "/";

	private final ObjectMapper objectMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private String userName = DEFAULT_USER_NAME;
	private String password = DEFAULT_PASSWORD;
	private CloseableHttpClient client;
	private String protocol = DEFAULT_PROTOCOL;
	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;

	public void close() throws IOException {
		if (client != null) {
			client.close();
			client = null;
		}
	}

	public String getUserName() {
		return userName;
	}

	public int getPort() {
		return port;
	}

	public String getProtocol() {
		return protocol;
	}

	public String getPassword() {
		return password;
	}

	public String getHost() {
		return host;
	}

	public Admin withHost(String host) {
		this.host = host;
		return this;
	}

	public Admin withProtocol(String protocol) {
		this.protocol = protocol;
		return this;
	}

	public Admin withPort(int port) {
		this.port = port;
		return this;
	}

	public Admin withUserName(String userName) {
		this.userName = userName;
		return this;
	}

	public Admin withPassword(String password) {
		this.password = password;
		return this;
	}

	private static String v1(String... segments) {
		return join(V1, segments);
	}

	private static String join(String path, String[] segments) {
		return path + String.join(PATH_SEPARATOR, segments);
	}

	private URI uri(String path) {
		try {
			return new URI(protocol, null, host, port, path, null, null);
		} catch (URISyntaxException x) {
			throw new IllegalArgumentException(x.getMessage(), x);
		}
	}

	private <T> T get(String path, Class<T> type) throws IOException, GeneralSecurityException {
		return get(path, SimpleType.constructUnsafe(type));
	}

	private <T> T get(String path, JavaType type) throws IOException, GeneralSecurityException {
		return read(header(new HttpGet(uri(path))), type, HttpStatus.SC_OK);
	}

	private <T> T delete(String path, Class<T> type) throws IOException, GeneralSecurityException {
		return delete(path, SimpleType.constructUnsafe(type));
	}

	private <T> T delete(String path, JavaType type) throws IOException, GeneralSecurityException {
		return read(header(new HttpDelete(uri(path))), type, HttpStatus.SC_OK);
	}

	private ClassicHttpRequest header(ClassicHttpRequest request) {
		request.setHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON);
		return request;
	}

	private <T> T post(String path, Object request, Class<T> responseType)
			throws IOException, GeneralSecurityException {
		return post(path, request, SimpleType.constructUnsafe(responseType));
	}

	private <T> T post(String path, Object request, JavaType responseType)
			throws IOException, GeneralSecurityException {
		HttpPost post = new HttpPost(uri(path));
		String json = objectMapper.writeValueAsString(request);
		log.debug("POST {}", json);
		post.setEntity(new StringEntity(json));
		return read(header(post), responseType, HttpStatus.SC_OK);
	}

	private <T> T read(ClassicHttpRequest request, JavaType type, int successCode)
			throws IOException, GeneralSecurityException {
		HttpHost target = new HttpHost(protocol, host, port);
		HttpClientContext localContext = HttpClientContext.create();
		BasicScheme basicAuth = new BasicScheme();
		basicAuth.initPreemptive(new UsernamePasswordCredentials(userName, password.toCharArray()));
		localContext.resetAuthExchange(target, basicAuth);
		CloseableHttpResponse response = client().execute(request, localContext);
		String json;
		try {
			json = EntityUtils.toString(response.getEntity());
		} catch (ParseException e) {
			throw new HttpResponseParsingException("Could not parse response", e);
		}
		if (response.getCode() == successCode) {
			return objectMapper.readValue(json, type);
		}
		throw new HttpResponseException(response.getCode(), response.getReasonPhrase() + " " + json);
	}

	private CloseableHttpClient client() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		if (client == null) {
			SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(new TrustAllStrategy()).build();
			SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
					.setSslContext(sslcontext).setHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
			HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
					.setSSLSocketFactory(sslSocketFactory).build();
			HttpClientBuilder clientBuilder = HttpClients.custom();
			clientBuilder.setConnectionManager(cm);
			client = clientBuilder.build();
		}
		return client;
	}

	private static class HttpResponseParsingException extends IOException {

		private static final long serialVersionUID = 1L;

		public HttpResponseParsingException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public List<InstalledModule> getModules() throws IOException, GeneralSecurityException {
		return get(v1(MODULES),
				objectMapper.getTypeFactory().constructCollectionType(List.class, InstalledModule.class));
	}

	public Database createDatabase(Database database) throws IOException, GeneralSecurityException {
		Map<String, InstalledModule> installedModules = new HashMap<>();
		for (InstalledModule module : getModules()) {
			installedModules.put(module.getName(), module);
		}
		for (ModuleConfig moduleConfig : database.getModules()) {
			if (!installedModules.containsKey(moduleConfig.getName())) {
				throw new IllegalArgumentException(String.format("Module %s not installed", moduleConfig.getName()));
			}
			moduleConfig.setId(installedModules.get(moduleConfig.getName()).getId());
		}
		Database response = post(v1(BDBS), database, Database.class);
		long uid = response.getUid();
		Awaitility.await().pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
				.until(() -> executeCommand(uid, new Command("PING")).getResponse().asBoolean());
		return response;
	}

	public List<Database> getDatabases() throws IOException, GeneralSecurityException {
		return get(v1(BDBS), objectMapper.getTypeFactory().constructCollectionType(List.class, Database.class));
	}

	public void deleteDatabase(long uid) {
		Awaitility.await().pollInterval(Duration.ofSeconds(1)).until(() -> {
			try {
				delete(v1(BDBS, String.valueOf(uid)), Database.class);
				return true;
			} catch (HttpResponseException e) {
				if (e.getStatusCode() == HttpStatus.SC_CONFLICT) {
					log.info("Could not delete database {}, retrying...", uid);
					return false;
				}
				throw e;
			}
		});
	}

	public void waitForBoostrap() {
		Awaitility.await().timeout(Duration.ofMinutes(1)).pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
				.until(() -> "idle".equals(getBootstrap().getStatus().getState()));

	}

	private Bootstrap getBootstrap() throws IOException, GeneralSecurityException {
		return get(v1(BOOTSTRAP), Bootstrap.class);
	}

	public CommandResponse executeCommand(long bdb, Command command) throws IOException, GeneralSecurityException {
		return post(v1(BDBS, String.valueOf(bdb), COMMAND), command, CommandResponse.class);
	}

	public static Admin create(String host) {
		Admin admin = new Admin();
		admin.withHost(host);
		return admin;
	}

	public static Admin create(String host, int port) {
		Admin admin = new Admin();
		admin.withHost(host);
		admin.withPort(port);
		return admin;
	}

}

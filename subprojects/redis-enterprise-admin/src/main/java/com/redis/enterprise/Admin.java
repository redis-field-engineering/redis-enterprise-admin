package com.redis.enterprise;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.ByteArrayBody;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
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
import org.apache.hc.core5.http.ContentType;
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
import com.redis.enterprise.rest.Action;
import com.redis.enterprise.rest.Bootstrap;
import com.redis.enterprise.rest.CommandResponse;
import com.redis.enterprise.rest.InstalledModule;
import com.redis.enterprise.rest.ModuleInstallResponse;

public class Admin implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(Admin.class);

	public static final String CONTENT_TYPE_JSON = "application/json";
	public static final String V1 = "/v1/";
	public static final String V2 = "/v2/";
	public static final String DEFAULT_PROTOCOL = "https";
	public static final String DEFAULT_HOST = "localhost";
	public static final int DEFAULT_PORT = 9443;
	public static final String BOOTSTRAP = "bootstrap";
	public static final String ACTIONS = "actions";
	public static final String MODULES = "modules";
	public static final String BDBS = "bdbs";
	public static final String COMMAND = "command";
	private static final CharSequence PATH_SEPARATOR = "/";

	private final ObjectMapper objectMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private final UsernamePasswordCredentials credentials;
	private final CloseableHttpClient client;
	private String protocol = DEFAULT_PROTOCOL;
	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;

	public Admin(String userName, final char[] password) throws GeneralSecurityException {
		this.credentials = new UsernamePasswordCredentials(userName, password);
		SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(new TrustAllStrategy()).build();
		SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
				.setSslContext(sslcontext).setHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
		HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
				.setSSLSocketFactory(sslSocketFactory).build();
		HttpClientBuilder clientBuilder = HttpClients.custom();
		clientBuilder.setConnectionManager(cm);
		this.client = clientBuilder.build();
	}

	@Override
	public void close() throws Exception {
		client.close();
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public void setPort(int port) {
		this.port = port;
	}

	private static String v1(String... segments) {
		return join(V1, segments);
	}

	private static String v2(String... segments) {
		return join(V2, segments);
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

	private <T> T get(String path, Class<T> type) throws ParseException, IOException {
		return get(path, SimpleType.constructUnsafe(type));
	}

	private <T> T get(String path, JavaType type) throws ParseException, IOException {
		return read(header(new HttpGet(uri(path))), type, HttpStatus.SC_OK);
	}

	private <T> T delete(String path, Class<T> type) throws ParseException, IOException {
		return delete(path, SimpleType.constructUnsafe(type));
	}

	private <T> T delete(String path, JavaType type) throws ParseException, IOException {
		return read(header(new HttpDelete(uri(path))), type, HttpStatus.SC_OK);
	}

	private ClassicHttpRequest header(ClassicHttpRequest request) {
		request.setHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON);
		return request;
	}

	private <T> T post(String path, Object request, Class<T> responseType) throws ParseException, IOException {
		return post(path, request, SimpleType.constructUnsafe(responseType));
	}

	private <T> T post(String path, Object request, JavaType responseType) throws ParseException, IOException {
		HttpPost post = new HttpPost(uri(path));
		String json = objectMapper.writeValueAsString(request);
		log.debug("POST {}", json);
		post.setEntity(new StringEntity(json));
		return read(header(post), responseType, HttpStatus.SC_OK);
	}

	private <T> T read(ClassicHttpRequest request, JavaType type, int successCode) throws IOException, ParseException {
		HttpHost target = new HttpHost(protocol, host, port);
		HttpClientContext localContext = HttpClientContext.create();
		if (credentials != null) {
			BasicScheme basicAuth = new BasicScheme();
			basicAuth.initPreemptive(credentials);
			localContext.resetAuthExchange(target, basicAuth);
		}
		CloseableHttpResponse response = client.execute(request, localContext);
		String json = EntityUtils.toString(response.getEntity());
		if (response.getCode() == successCode) {
			return objectMapper.readValue(json, type);
		}
		throw new HttpResponseException(response.getCode(), response.getReasonPhrase() + " " + json);
	}

	public List<InstalledModule> getModules() throws ParseException, IOException {
		return get(v1(MODULES),
				objectMapper.getTypeFactory().constructCollectionType(List.class, InstalledModule.class));
	}

	public Database createDatabase(Database database) throws ParseException, IOException {
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
		Awaitility.await().pollInterval(Duration.ofSeconds(1)).until(() -> {
			Command command = new Command();
			command.setCommand("PING");
			try {
				return executeCommand(uid, command).getResponse().asBoolean();
			} catch (HttpResponseException e) {
				log.info("PING unsuccessful, retrying...");
				return false;
			}
		});
		return response;
	}

	public List<Database> getDatabases() throws ParseException, IOException {
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

	public ModuleInstallResponse installModule(String filename, InputStream inputStream)
			throws ParseException, IOException {
		HttpPost post = new HttpPost(uri(v2(MODULES)));
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.STRICT);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IOUtils.copy(inputStream, baos);
		builder.addPart("module", new ByteArrayBody(baos.toByteArray(), ContentType.MULTIPART_FORM_DATA, filename));
		post.setEntity(builder.build());
		ModuleInstallResponse response = read(post, SimpleType.constructUnsafe(ModuleInstallResponse.class),
				HttpStatus.SC_ACCEPTED);
		baos.close();
		Awaitility.await().timeout(Duration.ofMinutes(1)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			log.info("Checking status of action {}", response.getActionUid());
			Action status = getAction(response.getActionUid());
			if ("completed".equals(status.getStatus())) {
				return true;
			}
			log.info("Action {} status: {}", response.getActionUid(), status.getStatus());
			return false;
		});
		return response;
	}

	public void waitForBoostrap() {
		Awaitility.await().pollInterval(Duration.ofSeconds(1)).timeout(Duration.ofMinutes(1))
				.until(() -> "idle".equals(getBootstrap().getStatus().getState()));

	}

	private Bootstrap getBootstrap() throws ParseException, IOException {
		return get(v1(BOOTSTRAP), Bootstrap.class);
	}

	private Action getAction(String uid) throws ParseException, IOException {
		return get(v1(ACTIONS, uid), Action.class);
	}

	public CommandResponse executeCommand(long bdb, Command command) throws ParseException, IOException {
		return post(v1(BDBS, String.valueOf(bdb), COMMAND), command, CommandResponse.class);
	}

}

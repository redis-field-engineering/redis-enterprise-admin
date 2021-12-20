package com.redis.enterprise;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.net.ssl.SSLContext;

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
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.redis.enterprise.rest.Action;
import com.redis.enterprise.rest.Bootstrap;
import com.redis.enterprise.rest.Command;
import com.redis.enterprise.rest.CommandResponse;
import com.redis.enterprise.rest.Database;
import com.redis.enterprise.rest.Module;
import com.redis.enterprise.rest.ModuleInstallResponse;

public class Admin {

	private static final Logger log = LoggerFactory.getLogger(Admin.class);

	public static final Object CONTENT_TYPE_JSON = "application/json";
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
	private String protocol = DEFAULT_PROTOCOL;
	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;

	public Admin(String userName, final char[] password) {
		this.credentials = new UsernamePasswordCredentials(userName, password);
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

	private <T> T get(String path, Class<T> type) throws ParseException, GeneralSecurityException, IOException {
		return get(path, SimpleType.constructUnsafe(type));
	}

	private <T> T get(String path, JavaType type) throws ParseException, GeneralSecurityException, IOException {
		return read(new HttpGet(uri(path)), type);
	}

	private <T> T post(String path, Object request, Class<T> responseType)
			throws ParseException, GeneralSecurityException, IOException {
		return post(path, request, SimpleType.constructUnsafe(responseType));
	}

	private ClassicHttpResponse delete(String path) throws GeneralSecurityException, IOException {
		return execute(new HttpDelete(uri(path)));
	}

	private <T> T post(String path, Object request, JavaType responseType)
			throws ParseException, GeneralSecurityException, IOException {
		HttpPost post = new HttpPost(uri(path));
		String json = objectMapper.writeValueAsString(request);
		post.setEntity(new StringEntity(json));
		log.info("POST {}", json);
		return read(post, responseType);
	}

	private <T> T read(ClassicHttpRequest request, JavaType type)
			throws ParseException, GeneralSecurityException, IOException {
		request.setHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON);
		return read(request, type, HttpStatus.SC_OK);
	}

	private <T> T read(ClassicHttpRequest request, Class<T> type, int successCode)
			throws ParseException, GeneralSecurityException, IOException {
		return read(request, SimpleType.constructUnsafe(type), successCode);
	}

	private <T> T read(ClassicHttpRequest request, JavaType type, int successCode)
			throws GeneralSecurityException, IOException, ParseException {
		ClassicHttpResponse response = execute(request);
		String responseString = EntityUtils.toString(response.getEntity());
		if (response.getCode() == successCode) {
			return objectMapper.readValue(responseString, type);
		}
		throw new HttpResponseException(response.getCode(), responseString);
	}

	private ClassicHttpResponse execute(ClassicHttpRequest request) throws GeneralSecurityException, IOException {
		SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(new TrustAllStrategy()).build();
		SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
				.setSslContext(sslcontext).setHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
		HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
				.setSSLSocketFactory(sslSocketFactory).build();
		HttpClientBuilder clientBuilder = HttpClients.custom();
		clientBuilder.setConnectionManager(cm);
		try (CloseableHttpClient client = clientBuilder.build()) {
			HttpHost target = new HttpHost(protocol, host, port);
			HttpClientContext localContext = HttpClientContext.create();
			if (credentials != null) {
				BasicScheme basicAuth = new BasicScheme();
				basicAuth.initPreemptive(credentials);
				localContext.resetAuthExchange(target, basicAuth);
			}
			return client.execute(request, localContext);
		}
	}

	public List<Module> getModules() throws ParseException, GeneralSecurityException, IOException {
		CollectionType type = objectMapper.getTypeFactory().constructCollectionType(List.class, Module.class);
		return get(v1(MODULES), type);
	}

	public Database createDatabase(Database database)
			throws ParseException, GeneralSecurityException, IOException {
		return post(v1(BDBS), database, Database.class);
	}

	public List<Database> getDatabases() throws ParseException, GeneralSecurityException, IOException {
		CollectionType type = objectMapper.getTypeFactory().constructCollectionType(List.class, Database.class);
		return get(v1(BDBS), type);
	}

	public void deleteDatabase(long uid) throws GeneralSecurityException, IOException {
		ClassicHttpResponse response = delete(v1(BDBS, String.valueOf(uid)));
		if (response.getCode() != HttpStatus.SC_OK) {
			throw new HttpResponseException(response.getCode(), response.getReasonPhrase());
		}
		log.info("Deleted database {}", uid);
	}

	public ModuleInstallResponse installModule(String filename, byte[] bytes)
			throws ParseException, GeneralSecurityException, IOException {
		HttpPost post = new HttpPost(uri(v2(MODULES)));
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.STRICT);
		builder.addPart("module", new ByteArrayBody(bytes, ContentType.MULTIPART_FORM_DATA, filename));
		post.setEntity(builder.build());
		return read(post, ModuleInstallResponse.class, HttpStatus.SC_ACCEPTED);
	}

	public Bootstrap getBootstrap() throws ParseException, GeneralSecurityException, IOException {
		return get(v1(BOOTSTRAP), Bootstrap.class);
	}

	public Action getAction(String uid) throws ParseException, GeneralSecurityException, IOException {
		return get(v1(ACTIONS, uid), Action.class);
	}

	public CommandResponse executeCommand(long bdb, Command command)
			throws ParseException, GeneralSecurityException, IOException {
		return post(v1(BDBS, String.valueOf(bdb), COMMAND), command, CommandResponse.class);
	}

}

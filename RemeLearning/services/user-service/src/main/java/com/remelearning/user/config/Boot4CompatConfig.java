package com.remelearning.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.web.client.RestClient;

/**
 * Local Maven cache is missing several Spring Boot 4 split-module autoconfigure jars
 * (spring-boot-jackson-autoconfigure, spring-boot-kafka-autoconfigure, a RestClient.Builder
 * autoconfiguration, spring-boot-flyway's FlywayAutoConfiguration, and mybatis-spring-boot-starter
 * 3.0.4's own legacy spring.factories registration isn't read by Boot 4 at all). Define the
 * affected beans explicitly here until the environment/dependency versions are reconciled.
 */
@Configuration
public class Boot4CompatConfig {

	@Value("${mybatis.mapper-locations:classpath:mapper/**/*.xml}")
	private String mapperLocations;

	@Bean
	public Flyway flyway(
			DataSource dataSource,
			@Value("${spring.flyway.locations:classpath:db/migration}") String locations,
			@Value("${spring.flyway.schemas:}") String schemas,
			@Value("${spring.flyway.create-schemas:true}") boolean createSchemas,
			@Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate) {
		var configBuilder = Flyway.configure()
				.dataSource(dataSource)
				.locations(locations)
				.createSchemas(createSchemas)
				.baselineOnMigrate(baselineOnMigrate);
		if (!schemas.isBlank()) {
			configBuilder.schemas(schemas.split(","));
		}
		Flyway flyway = configBuilder.load();
		flyway.repair();
		flyway.migrate();
		return flyway;
	}

	@Bean
	public SqlSessionFactory sqlSessionFactory(DataSource dataSource, Flyway flyway) throws Exception {
		SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
		factoryBean.setDataSource(dataSource);
		factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocations));
		var configuration = new org.apache.ibatis.session.Configuration();
		configuration.setMapUnderscoreToCamelCase(true);
		factoryBean.setConfiguration(configuration);
		return factoryBean.getObject();
	}

	@Bean
	public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
		return new SqlSessionTemplate(sqlSessionFactory);
	}

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	public ProducerFactory<String, Object> kafkaProducerFactory(
			@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
		Map<String, Object> configProps = new HashMap<>();
		configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		return new DefaultKafkaProducerFactory<>(configProps);
	}

	@Bean
	public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> kafkaProducerFactory) {
		return new KafkaTemplate<>(kafkaProducerFactory);
	}

	@Bean
	@ConditionalOnMissingBean(RestClient.Builder.class)
	public RestClient.Builder restClientBuilder(ObjectMapper objectMapper) {
		// Register Jackson converter so .body(someRecord) serializes to JSON correctly —
		// without this the RestClient sends an empty body (Boot 4.1.0 no longer auto-registers
		// a RestClient.Builder bean with pre-configured converters).
		// Also force SimpleClientHttpRequestFactory: RestClient's default factory pick in this
		// environment (JDK HttpClient) silently sends an EMPTY body over real HTTP for POST
		// requests, even though the same request looks fine in mocked/unit tests
		// (MockRestServiceServer bypasses the real transport) — server sees "Field required" at
		// ["body"]. SimpleClientHttpRequestFactory fully buffers the request body before
		// sending, avoiding that streaming bug.
		return RestClient.builder()
				.requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
				.messageConverters(converters -> converters.add(
						new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper)));
	}
}

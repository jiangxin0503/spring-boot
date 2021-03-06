/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.data.redis;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import redis.clients.jedis.Jedis;

import org.springframework.boot.junit.runner.classpath.ClassPathExclusions;
import org.springframework.boot.junit.runner.classpath.ModifiedClassPathRunner;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RedisAutoConfiguration} when Lettuce is not on the classpath.
 *
 * @author Mark Paluch
 * @author Stephane Nicoll
 */
@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions("lettuce-core-*.jar")
public class RedisAutoConfigurationJedisTests {

	private AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void testOverrideRedisConfiguration() throws Exception {
		load("spring.redis.host:foo", "spring.redis.database:1");
		JedisConnectionFactory cf = this.context.getBean(JedisConnectionFactory.class);
		assertThat(cf.getHostName()).isEqualTo("foo");
		assertThat(cf.getDatabase()).isEqualTo(1);
		assertThat(cf.getPassword()).isNull();
		assertThat(cf.isUseSsl()).isFalse();
	}

	@Test
	public void testRedisUrlConfiguration() throws Exception {
		load("spring.redis.host:foo",
				"spring.redis.url:redis://user:password@example:33");
		JedisConnectionFactory cf = this.context.getBean(JedisConnectionFactory.class);
		assertThat(cf.getHostName()).isEqualTo("example");
		assertThat(cf.getPort()).isEqualTo(33);
		assertThat(cf.getPassword()).isEqualTo("password");
		assertThat(cf.isUseSsl()).isFalse();
	}

	@Test
	public void testOverrideUrlRedisConfiguration() throws Exception {
		load("spring.redis.host:foo", "spring.redis.password:xyz",
				"spring.redis.port:1000", "spring.redis.ssl:false",
				"spring.redis.url:rediss://user:password@example:33");
		JedisConnectionFactory cf = this.context.getBean(JedisConnectionFactory.class);
		assertThat(cf.getHostName()).isEqualTo("example");
		assertThat(cf.getPort()).isEqualTo(33);
		assertThat(cf.getPassword()).isEqualTo("password");
		assertThat(cf.isUseSsl()).isTrue();
	}

	@Test
	public void testRedisConfigurationWithPool() throws Exception {
		load("spring.redis.host:foo", "spring.redis.jedis.pool.min-idle:1",
				"spring.redis.jedis.pool.max-idle:4",
				"spring.redis.jedis.pool.max-active:16",
				"spring.redis.jedis.pool.max-wait:2000");
		JedisConnectionFactory cf = this.context.getBean(JedisConnectionFactory.class);
		assertThat(cf.getHostName()).isEqualTo("foo");
		assertThat(cf.getPoolConfig().getMinIdle()).isEqualTo(1);
		assertThat(cf.getPoolConfig().getMaxIdle()).isEqualTo(4);
		assertThat(cf.getPoolConfig().getMaxTotal()).isEqualTo(16);
		assertThat(cf.getPoolConfig().getMaxWaitMillis()).isEqualTo(2000);
	}

	@Test
	public void testRedisConfigurationWithTimeout() throws Exception {
		load("spring.redis.host:foo", "spring.redis.timeout:100");
		JedisConnectionFactory cf = this.context.getBean(JedisConnectionFactory.class);
		assertThat(cf.getHostName()).isEqualTo("foo");
		assertThat(cf.getTimeout()).isEqualTo(100);
	}

	@Test
	public void testRedisConfigurationWithSentinel() throws Exception {
		List<String> sentinels = Arrays.asList("127.0.0.1:26379", "127.0.0.1:26380");
		if (isAtLeastOneNodeAvailable(sentinels)) {
			load("spring.redis.sentinel.master:mymaster", "spring.redis.sentinel.nodes:"
					+ StringUtils.collectionToCommaDelimitedString(sentinels));
			assertThat(this.context.getBean(JedisConnectionFactory.class)
					.isRedisSentinelAware()).isTrue();
		}
	}

	@Test
	public void testRedisConfigurationWithCluster() throws Exception {
		List<String> clusterNodes = Arrays.asList("127.0.0.1:27379", "127.0.0.1:27380");
		if (isAtLeastOneNodeAvailable(clusterNodes)) {
			load("spring.redis.cluster.nodes[0]:" + clusterNodes.get(0),
					"spring.redis.cluster.nodes[1]:" + clusterNodes.get(1));
			assertThat(this.context.getBean(JedisConnectionFactory.class)
					.getClusterConnection()).isNotNull();
		}
	}

	private void load(String... environment) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		EnvironmentTestUtils.addEnvironment(ctx, environment);
		ctx.register(RedisAutoConfiguration.class);
		ctx.refresh();
		this.context = ctx;
	}

	private boolean isAtLeastOneNodeAvailable(List<String> nodes) {
		for (String node : nodes) {
			if (isAvailable(node)) {
				return true;
			}
		}

		return false;
	}

	private boolean isAvailable(String node) {
		Jedis jedis = null;
		try {
			String[] hostAndPort = node.split(":");
			jedis = new Jedis(hostAndPort[0], Integer.valueOf(hostAndPort[1]));
			jedis.connect();
			jedis.ping();
			return true;
		}
		catch (Exception ex) {
			return false;
		}
		finally {
			if (jedis != null) {
				try {
					jedis.disconnect();
					jedis.close();
				}
				catch (Exception ex) {
					// Continue
				}
			}
		}
	}

}

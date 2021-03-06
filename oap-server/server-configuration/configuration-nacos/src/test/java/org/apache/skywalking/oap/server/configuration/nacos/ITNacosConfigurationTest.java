/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.configuration.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * @author kezhenxu94
 */
public class ITNacosConfigurationTest {
    private final Yaml yaml = new Yaml();

    private NacosConfigurationTestProvider provider;

    @Before
    public void setUp() throws Exception {
        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        provider =
            (NacosConfigurationTestProvider) moduleManager
                .find(NacosConfigurationTestModule.NAME)
                .provider();

        assertNotNull(provider);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Ignore // because of instability
    @Test(timeout = 10000)
    public void shouldReadUpdated() throws NacosException {
        assertNull(provider.watcher.value());

        final Properties properties = new Properties();
        properties.put("serverAddr", "localhost:8848");
        final ConfigService configService = NacosFactory.createConfigService(properties);
        assertTrue(configService.publishConfig("test-module.default.testKey", "skywalking", "500"));

        for (String v = provider.watcher.value(); v == null; v = provider.watcher.value()) {
        }

        assertEquals("500", provider.watcher.value());

        assertTrue(configService.removeConfig("test-module.default.testKey", "skywalking"));

        for (String v = provider.watcher.value(); v != null; v = provider.watcher.value()) {
        }

        assertNull(provider.watcher.value());
    }

    @SuppressWarnings("unchecked")
    private void loadConfig(ApplicationConfiguration configuration) throws FileNotFoundException {
        Reader applicationReader = ResourceUtils.read("application.yml");
        Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            moduleConfig.forEach((moduleName, providerConfig) -> {
                if (providerConfig.size() > 0) {
                    ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(moduleName);
                    providerConfig.forEach((name, propertiesConfig) -> {
                        Properties properties = new Properties();
                        if (propertiesConfig != null) {
                            propertiesConfig.forEach((key, value) -> {
                                properties.put(key, value);
                                final Object replaceValue = yaml.load(PropertyPlaceholderHelper.INSTANCE
                                    .replacePlaceholders(value + "", properties));
                                if (replaceValue != null) {
                                    properties.replace(key, replaceValue);
                                }
                            });
                        }
                        moduleConfiguration.addProviderConfiguration(name, properties);
                    });
                }
            });
        }
    }
}

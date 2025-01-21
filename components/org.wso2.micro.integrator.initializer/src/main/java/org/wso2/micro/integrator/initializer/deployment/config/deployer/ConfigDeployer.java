/*
 *  Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.micro.integrator.initializer.deployment.config.deployer;

import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.commons.property.PropertyHolder;
import org.apache.synapse.transport.nhttp.config.SslSenderTrustStoreHolder;
import org.wso2.micro.application.deployer.CarbonApplication;
import org.wso2.micro.application.deployer.config.ApplicationConfiguration;
import org.wso2.micro.application.deployer.config.Artifact;
import org.wso2.micro.application.deployer.config.CappFile;
import org.wso2.micro.application.deployer.handler.AppDeploymentHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class ConfigDeployer implements AppDeploymentHandler {

    private static final Log log = LogFactory.getLog(ConfigDeployer.class);

    private static final String PROPERTY_TYPE = "config/property";

    private static final String LOCAL_CONFIG_FILE_NAME = "config.properties";
    private static final String GLOBAL_CONFIG_FILE_NAME = "file.properties";
    private Properties globalProperties;

    public static final char URL_SEPARATOR_CHAR = '/';

    public ConfigDeployer() {
    }

    @Override
    public void deployArtifacts(CarbonApplication carbonApp, AxisConfiguration axisConfig)
            throws DeploymentException {
        if (log.isDebugEnabled()) {
            log.debug("Deploying properties  - " + carbonApp.getAppName());
        }
        ApplicationConfiguration appConfig = carbonApp.getAppConfig();
        List<Artifact.Dependency> deps = appConfig.getApplicationArtifact().getDependencies();

        List<Artifact> artifacts = new ArrayList<Artifact>();
        for (Artifact.Dependency dep : deps) {
            if (dep.getArtifact() != null) {
                artifacts.add(dep.getArtifact());
            }
        }
        deployConfigArtifacts(artifacts);
    }

    @Override
    public void undeployArtifacts(CarbonApplication carbonApp, AxisConfiguration axisConfig)
            throws DeploymentException {

    }

    private void deployConfigArtifacts(List<Artifact> artifacts) {
        artifacts.stream().filter(artifact -> PROPERTY_TYPE.equals(artifact.getType())).forEach(artifact -> {
            if (log.isDebugEnabled()) {
                log.debug("Deploying config artifact: " + artifact.getName());
            }
            writePropertyToMap(artifact);
        });
    }

    private void writePropertyToMap(Artifact artifact) {
        // get the file path of the registry config file
        List<CappFile> files = artifact.getFiles();
        if (files.size() == 1) {
            Path confFolder = Paths.get(getHome(), "conf");
            Path globalPropertiesFilePath = confFolder.resolve(GLOBAL_CONFIG_FILE_NAME) ;
            Path serverConfPropertyPath = confFolder.resolve(LOCAL_CONFIG_FILE_NAME);
            String configFilePath = artifact.getExtractedPath() + File.separator + LOCAL_CONFIG_FILE_NAME;
            processConfFile(artifact.getName(), configFilePath, globalPropertiesFilePath.toString(),
                    serverConfPropertyPath.toString());
        } else {
            log.error("config/property type must have a single file which declares " +
                    "config. But " + files.size() + " files found.");
        }
    }

    private void processConfFile(String integrationName, String configFilePath, String globalPropertiesFilePath,
                                 String serverConfPropertyPath) {
        File configFile = new File(configFilePath);
        // Load capp conf property file
        Properties configProperties = loadPropertiesFromFile(configFile);
        // Load global conf property file
        this.globalProperties = loadPropertiesFromFile(new File(globalPropertiesFilePath));
        // Load sever conf property file
        Properties serverConfigProperties = loadPropertiesFromFile(new File(serverConfPropertyPath));

        Properties newServerConfigProperties = new Properties();
        if (serverConfigProperties.isEmpty() && configProperties.isEmpty() ) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("No configuration is used in the integration[%s]", integrationName));
            }
        } else {
            for (Map.Entry<Object, Object> entry : serverConfigProperties.entrySet()) {
                String key = entry.getKey().toString();
                String type = entry.getValue().toString();
                if (configProperties.containsKey(key)) {
                    type = configProperties.getProperty(key);
                    configProperties.remove(key);
                }
                newServerConfigProperties.setProperty(key, type);
                processConfigProperties(key, type);
            }
            for (Map.Entry<Object, Object> entry : configProperties.entrySet()) {
                String key = entry.getKey().toString();
                String type = entry.getValue().toString();
                newServerConfigProperties.setProperty(key, type);
                processConfigProperties(key, type);
            }
            writeServerConfFile(serverConfPropertyPath, newServerConfigProperties);
        }
    }

    private void processConfigProperties(String key, String type) {
        String value = getValueOfKey(key);
        if (value != null) {
            if (Objects.equals(type, "cert")) {
                deployCert(key, value);
            }
            if (PropertyHolder.getInstance().hasKey(key)) {
                String oldValue = PropertyHolder.getInstance().getPropertyValue(key);
                if (!Objects.equals(oldValue, value)) {
                    log.info(String.format("The value of the key:[%s] has been " +
                            "replaced with the new value.", key));
                }
            }
            PropertyHolder.getInstance().setProperty(key, value);
        } else {
            log.error(String.format("The value of the key:[%s] is not found.", key));
        }
    }

    private void deployCert(String key, String path) {
        // Load the truststore properties
        char[] password = SslSenderTrustStoreHolder.getInstance().getPassword().toCharArray();
        String type = SslSenderTrustStoreHolder.getInstance().getType();
        Path trustStorePath = Paths.get(getHome(), SslSenderTrustStoreHolder.getInstance().getLocation());
        try (FileInputStream trustStoreStream = new FileInputStream(trustStorePath.toFile())) {
            KeyStore trustStore = KeyStore.getInstance(type);
            trustStore.load(trustStoreStream, password);
            if (!trustStore.containsAlias(key)) {
                // Load the certificate file
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                try (FileInputStream certStream = new FileInputStream(path)) {
                    Certificate cert = certFactory.generateCertificate(certStream);
                    // Add the certificate to the truststore
                    trustStore.setCertificateEntry(key, cert);
                    log.info("Certificate added with alias: " + key);
                }
                // Save the truststore with the new certificate
                try (FileOutputStream outputStream = new FileOutputStream(trustStorePath.toFile())) {
                    trustStore.store(outputStream, password);
                    log.info("Truststore updated successfully at: " + trustStorePath);
                }
            } else {
                log.info(String.format("The trust store already contains a certificate " +
                        "with the alias [%s].", key));
            }
        } catch (FileNotFoundException e) {
            log.error(String.format("File not found for importing the certificate: %s", key));
        } catch (IOException e) {
            log.error(String.format("Certificate import failed: %s", key));
        } catch (CertificateException e) {
            log.error(String.format("An error occurred while processing the certificate: %s", key));
        } catch (KeyStoreException e) {
            log.error(String.format("An error occurred while processing the truststore: %s", key));
        } catch (NoSuchAlgorithmException e) {
            log.error(String.format("An error occurred while loading the certificate: %s", key));
        }
    }

    private void writeServerConfFile(String file, Properties newServerConfigProperties) {
        try (FileWriter writer = new FileWriter(file)) {
            Enumeration<?> propertyNames = newServerConfigProperties.propertyNames();
            while (propertyNames.hasMoreElements()) {
                String key = (String) propertyNames.nextElement();
                String value = newServerConfigProperties.getProperty(key);
                writer.write(key + ":" + value + "\n");
            }
        } catch (IOException e) {
            log.error("Failed to add the config.properties file to the server conf folder: "
                    + e.getMessage());
        }
    }

    private Properties loadPropertiesFromFile(File file) {
        Properties properties = new Properties();
        if (file.exists()) {
            try (FileInputStream serverConfigFileReader = new FileInputStream(file)) {
                properties.load(serverConfigFileReader);
            } catch (IOException e) {
                log.error("Error occurred while loading properties from file:" + e.getMessage());
            }
        }
        return properties;
    }

    private String getValueOfKey(String key) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
            if (value == null) {
                value = this.globalProperties.getProperty(key);
            }
        }
        return value;
    }

    private String getHome() {
        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome == null || "".equals(carbonHome) || ".".equals(carbonHome)) {
            carbonHome = getSystemDependentPath(new File(".").getAbsolutePath());
        }
        return carbonHome;
    }

    private String getSystemDependentPath(String path) {
        return path.replace(URL_SEPARATOR_CHAR, File.separatorChar);
    }
}

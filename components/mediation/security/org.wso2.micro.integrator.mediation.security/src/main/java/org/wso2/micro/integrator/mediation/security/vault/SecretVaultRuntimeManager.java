/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.mediation.security.vault;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.mediation.security.vault.util.SecureVaultUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages runtime secret updates for cipher-text.properties without requiring a server restart.
 *
 * Secrets added or updated via the Management API are held here and persisted to disk.
 * The file watcher calls reloadFromFile() to keep this store in sync across cluster nodes.
 * SecretCipherHander consults this store before falling through to the static FileBaseSecretRepository.
 */
public class SecretVaultRuntimeManager {

    private static final Log LOG = LogFactory.getLog(SecretVaultRuntimeManager.class);

    private static final SecretVaultRuntimeManager INSTANCE = new SecretVaultRuntimeManager();

    private static final String SECRET_REPOSITORIES_FILE_LOCATION = "secretRepositories.file.location";
    private static final String DEFAULT_CIPHER_TEXT_FILE = "cipher-text.properties";
    private static final String TMP_SUFFIX = ".tmp";

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    // alias → decrypted plaintext value
    private final Map<String, String> runtimeSecrets = new ConcurrentHashMap<>();

    private volatile String cipherTextPropertiesPath;

    private SecretVaultRuntimeManager() {
    }

    public static SecretVaultRuntimeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Adds or updates a secret at runtime.
     * The caller provides ciphertext pre-encrypted with the server's keystore (e.g., via MI CLI).
     * The ciphertext is decrypted, stored in memory, and persisted to cipher-text.properties.
     *
     * @param alias             the secret alias
     * @param encryptedCiphertext the Base64-encoded RSA ciphertext
     * @throws RuntimeException if decryption or file persistence fails
     */
    public void addOrUpdateSecret(String alias, String encryptedCiphertext) throws IOException {
        String decrypted = SecureVaultUtils.decryptSecret(encryptedCiphertext);
        lock.writeLock().lock();
        try {
            String previous = runtimeSecrets.put(alias, decrypted);
            try {
                persistToFile(alias, encryptedCiphertext, false);
            } catch (IOException e) {
                if (previous == null) {
                    runtimeSecrets.remove(alias);
                } else {
                    runtimeSecrets.put(alias, previous);
                }
                throw e;
            }
        } finally {
            lock.writeLock().unlock();
        }
        LOG.info("Secret alias '" + alias + "' added/updated at runtime");
    }

    /**
     * Removes a secret by alias at runtime.
     * Removes from memory and from cipher-text.properties on disk.
     *
     * @param alias the secret alias
     * @return true if found and removed; false if the alias does not exist
     */
    public boolean removeSecret(String alias) throws IOException {
        lock.writeLock().lock();
        try {
            Properties fileProps = loadCipherTextFile();
            if (!runtimeSecrets.containsKey(alias) && !fileProps.containsKey(alias)) {
                return false;
            }
            String previous = runtimeSecrets.remove(alias);
            try {
                persistToFile(alias, null, true);
            } catch (IOException e) {
                if (previous != null) {
                    runtimeSecrets.put(alias, previous);
                }
                throw e;
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the decrypted value for the given alias, or null if not managed at runtime.
     *
     * @param alias the secret alias
     * @return decrypted plaintext, or null
     */
    public String getSecret(String alias) {
        return runtimeSecrets.get(alias);
    }

    /**
     * Returns all known alias names: keys from cipher-text.properties (covers startup entries)
     * merged with any in-memory runtime keys not yet flushed.
     *
     * @return unmodifiable set of all alias names
     */
    public Set<String> getAllAliases() {
        Properties fileProps;
        try {
            fileProps = loadCipherTextFile();
        } catch (IOException e) {
            LOG.warn("Failed to read cipher-text.properties; returning only in-memory aliases", e);
            return new LinkedHashSet<>(runtimeSecrets.keySet());
        }
        Set<String> aliases = new LinkedHashSet<>(fileProps.stringPropertyNames());
        aliases.addAll(runtimeSecrets.keySet());
        return aliases;
    }

    /**
     * Reloads all entries from cipher-text.properties into the runtime store.
     * Called at startup and by the file watcher when the file changes (cluster sync).
     * Failures per alias are logged and skipped; the store reflects whatever could be decrypted.
     */
    public void reloadFromFile() {
        Properties fileProps;
        try {
            fileProps = loadCipherTextFile();
        } catch (IOException e) {
            LOG.warn("Failed to read cipher-text.properties; runtime secret store not modified", e);
            return;
        }
        if (fileProps.isEmpty()) {
            lock.writeLock().lock();
            try {
                runtimeSecrets.clear();
            } finally {
                lock.writeLock().unlock();
            }
            LOG.info("cipher-text.properties is empty; runtime secret store cleared");
            return;
        }

        Map<String, String> fresh = new ConcurrentHashMap<>();
        for (String alias : fileProps.stringPropertyNames()) {
            String ciphertext = fileProps.getProperty(alias);
            if (ciphertext == null || ciphertext.trim().isEmpty()) {
                continue;
            }
            try {
                fresh.put(alias, SecureVaultUtils.decryptSecret(ciphertext));
            } catch (Exception e) {
                LOG.warn("Failed to decrypt secret for alias '" + alias + "' during reload: " + e.getMessage());
            }
        }

        lock.writeLock().lock();
        try {
            runtimeSecrets.clear();
            runtimeSecrets.putAll(fresh);
        } finally {
            lock.writeLock().unlock();
        }
        LOG.info("cipher-text.properties reloaded; " + fresh.size() + " secret(s) loaded into runtime store");
    }

    /**
     * Returns true if the file-based secure vault (cipher-text.properties) is configured and available.
     * Returns false when an external vault provider (e.g. AWS Secrets Manager, HashiCorp Vault) is
     * in use, because those providers bypass cipher-text.properties entirely.
     */
    public boolean isAvailable() {
        return getCipherTextPropertiesPath() != null;
    }

    /**
     * Returns the resolved path to cipher-text.properties, or null if it cannot be determined.
     */
    public String getCipherTextPropertiesPath() {
        if (cipherTextPropertiesPath == null) {
            synchronized (this) {
                if (cipherTextPropertiesPath == null) {
                    cipherTextPropertiesPath = resolveCipherTextPath();
                }
            }
        }
        return cipherTextPropertiesPath;
    }

    // ---- private helpers ----

    private void persistToFile(String alias, String encryptedCiphertext, boolean remove) throws IOException {
        String path = getCipherTextPropertiesPath();
        if (path == null) {
            LOG.warn("cipher-text.properties path is not configured; secret for alias '"
                    + alias + "' will not be persisted across restarts");
            return;
        }

        File target = new File(path);
        Properties fileProps = loadCipherTextFile();

        if (remove) {
            fileProps.remove(alias);
        } else {
            fileProps.setProperty(alias, encryptedCiphertext);
        }

        File temp = new File(path + TMP_SUFFIX);
        try {
            writeCipherTextFile(fileProps, temp);
            atomicReplace(temp, target);
        } catch (IOException e) {
            LOG.error("Failed to persist secret '" + alias + "' to cipher-text.properties", e);
            temp.delete();
            throw e;
        }
    }

    private Properties loadCipherTextFile() throws IOException {
        Properties props = new Properties();
        String path = getCipherTextPropertiesPath();
        if (path == null) {
            return props;
        }
        File file = new File(path);
        if (!file.exists()) {
            return props;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        }
        return props;
    }

    /**
     * Writes properties without the timestamp comment that Properties.store() adds,
     * to avoid triggering spurious file-watcher events on every write.
     */
    private void writeCipherTextFile(Properties props, File target) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.ISO_8859_1))) {
            props.stringPropertyNames().stream().sorted().forEach(key ->
                    pw.println(key + "=" + props.getProperty(key)));
        }
    }

    private void atomicReplace(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String resolveCipherTextPath() {
        Properties secretConf = SecureVaultUtil.loadProperties();
        String location = secretConf.getProperty(SECRET_REPOSITORIES_FILE_LOCATION);

        if (location != null && !location.trim().isEmpty()) {
            File f = new File(location.trim());
            if (f.isAbsolute()) {
                return f.getPath();
            }
            // Relative path — resolve against carbon.home
            String carbonHome = System.getProperty("carbon.home", ".");
            return Paths.get(carbonHome, location.trim()).toString();
        }

        // Default: {conf.location}/security/cipher-text.properties
        String confLocation = System.getProperty(SecureVaultConstants.CONF_LOCATION);
        if (confLocation == null) {
            confLocation = Paths.get("repository", "conf").toString();
        }
        String defaultPath = Paths.get(confLocation, SecureVaultConstants.SECURITY_DIR,
                DEFAULT_CIPHER_TEXT_FILE).toString();

        if (!new File(defaultPath).exists()) {
            LOG.debug("cipher-text.properties not found at default location: " + defaultPath);
            return null;
        }
        return defaultPath;
    }
}

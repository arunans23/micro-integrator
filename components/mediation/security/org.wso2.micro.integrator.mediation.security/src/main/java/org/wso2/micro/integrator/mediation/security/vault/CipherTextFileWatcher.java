/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
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

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Watches cipher-text.properties for file system changes and reloads the runtime secret store.
 *
 * In a clustered deployment with cipher-text.properties on a shared mount, a write from one node's
 * Management API call triggers this watcher on all other nodes, keeping secrets in sync without
 * a server restart. Other nodes' Synapse cache entries expire within the configured TTL (default 10 s).
 */
public class CipherTextFileWatcher implements Runnable {

    private static final Log LOG = LogFactory.getLog(CipherTextFileWatcher.class);

    private final Path watchedFile;
    private final Path watchedDir;
    private volatile WatchService watchService;

    public CipherTextFileWatcher(String cipherTextPropertiesPath) {
        this.watchedFile = Paths.get(cipherTextPropertiesPath).toAbsolutePath().normalize();
        this.watchedDir = this.watchedFile.getParent();
    }

    @Override
    public void run() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerDirectory();
            LOG.info("CipherTextFileWatcher started; watching: " + watchedFile);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        // Re-register to avoid silently missing events after overflow
                        LOG.warn("WatchService overflow event; re-registering watcher");
                        registerDirectory();
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = watchedDir.resolve(pathEvent.context()).normalize();

                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY && changed.equals(watchedFile)) {
                        LOG.info("cipher-text.properties changed; reloading runtime secret store");
                        try {
                            SecretVaultRuntimeManager.getInstance().reloadFromFile();
                        } catch (Exception e) {
                            LOG.error("Failed to reload runtime secret store after file change", e);
                        }
                    }
                }

                if (!key.reset()) {
                    LOG.warn("Watch key invalidated; directory may have been deleted. Stopping file watcher.");
                    break;
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to initialize CipherTextFileWatcher", e);
        } finally {
            stopWatchService();
        }
        LOG.info("CipherTextFileWatcher stopped");
    }

    public void stop() {
        Thread.currentThread().interrupt();
        stopWatchService();
    }

    private void registerDirectory() throws IOException {
        watchedDir.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.OVERFLOW);
    }

    private void stopWatchService() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.debug("Error closing WatchService", e);
            }
        }
    }
}

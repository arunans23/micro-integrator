/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.initializer.deployment.application.deployer;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.deployment.AbstractDeployer;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.application.deployer.AppDeployerUtils;
import org.wso2.micro.core.util.CarbonException;

import java.io.File;

public class CappDeployer extends AbstractDeployer {

    private static final Log log = LogFactory.getLog(CappDeployer.class);

    private AxisConfiguration axisConfig;
    private String cAppDir;
    private String extension;

    public void init(ConfigurationContext configurationContext) {
        if (log.isDebugEnabled()) {
            log.debug("Initializing Capp Deployer..");
        }
        this.axisConfig = configurationContext.getAxisConfiguration();
    }

    public void setDirectory(String cAppDir) {
        this.cAppDir = cAppDir;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    /**
     * Axis2 deployment engine will call this method when a .car archive is deployed. So we only have to call the
     * cAppDeploymentManager to deploy it using the absolute path of the deployed .car file.
     *
     * @param deploymentFileData - info about the deployed file
     * @throws DeploymentException - error while deploying cApp
     */
    public void deploy(DeploymentFileData deploymentFileData) throws DeploymentException {

        String artifactPath = deploymentFileData.getAbsolutePath();
        try {
            deployCarbonApps(artifactPath);
        } catch (Exception e) {
            log.error("Error while deploying carbon application " + artifactPath, e);
        }
        super.deploy(deploymentFileData);
    }

    /**
     * Deploy synapse artifacts in a carbon application.
     *
     * @param artifactPath - file path to be processed
     * @throws CarbonException - error while building
     */
    private void deployCarbonApps(String artifactPath) throws CarbonException {

        File cAppDirectory = new File(this.cAppDir);

        String archPathToProcess = AppDeployerUtils.formatPath(artifactPath);
        String cAppName = archPathToProcess.substring(archPathToProcess.lastIndexOf('/') + 1);

        if (!isCAppArchiveFile(cAppName)) {
            log.warn("Only .car files are processed. Hence " + cAppName + " will be ignored");
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Carbon Application detected : " + cAppName);
        }

        String targetCAppPath = cAppDirectory + File.separator + cAppName;
        if (!extractCarbonApplication(targetCAppPath, cAppName)) {
            log.error("Error while extracting carbon application : " + cAppName);
        }
    }

    /**
     * Builds the carbon application from app configuration created using the artifacts.xml path.
     *
     * @param targetCAppPath - path to target carbon application
     * @param cAppName       - name of the carbon application
     * @return - CarbonApplication instance if successfull. otherwise null..
     * @throws CarbonException - error while building
     */
    private boolean extractCarbonApplication(String targetCAppPath, String cAppName) throws CarbonException {
        return AppDeployerUtils.extractCAppToDirectory(targetCAppPath, cAppName);
    }

    /**
     * Checks whether a given file is a jar or an aar file.
     *
     * @param filename file to check
     * @return Returns boolean.
     */
    private boolean isCAppArchiveFile(String filename) {
        return (filename.endsWith(extension));
    }

    /**
     * Undeploys the cApp from system when the .car file is deleted from the repository. Find the relevant cApp using
     * the file path and call the undeploy method on applicationManager.
     *
     * @param filePath - deleted .car file path
     * @throws DeploymentException - error while un-deploying cApp
     */
    public void undeploy(String filePath) throws DeploymentException {

        super.undeploy(filePath);
        if (!AppDeployerUtils.deleteExtractedCApp(filePath)) {
            log.error("Error while deleting undeployed carbon application : " + filePath);
        }
    }
}

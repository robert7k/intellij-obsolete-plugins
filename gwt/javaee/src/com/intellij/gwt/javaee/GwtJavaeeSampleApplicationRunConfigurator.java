// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gwt.javaee;

import com.intellij.execution.RunManager;
import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.gwt.facet.GwtFacet;
import com.intellij.gwt.facet.GwtSampleApplicationRunConfigurator;
import com.intellij.gwt.packaging.GwtCompilerOutputElement;
import com.intellij.gwt.run.GwtRunConfiguration;
import com.intellij.javaee.appServers.deployment.DeploymentSettings;
import com.intellij.javaee.appServers.run.configuration.CommonStrategy;
import com.intellij.javaee.artifact.JavaeeArtifactUtil;
import com.intellij.javaee.web.artifact.WebArtifactUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.gwt.facet.GwtFacetFrameworkSupportProvider.addServletJarToArtifact;
import static com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRun;

/**
 * If the project already has a Java EE application server run configuration, deploys the GWT sample application there
 * and adds "DevMode"/"NoServer" run configurations that run the GWT dev mode against that server.
 */
final class GwtJavaeeSampleApplicationRunConfigurator implements GwtSampleApplicationRunConfigurator {
  @Override
  public void configure(@NotNull GwtFacet gwtFacet,
                        @Nullable WebFacet webFacet,
                        @NotNull GwtRunConfiguration gwtRunConfiguration,
                        @NotNull String gwtOutputRelativePath) {
    if (webFacet == null) return;

    Module module = gwtFacet.getModule();
    Project project = module.getProject();
    RunManager runManager = RunManager.getInstance(project);

    CommonStrategy javaEeConfiguration = null;
    for (RunConfiguration configuration : runManager.getAllConfigurationsList()) {
      if (configuration instanceof CommonStrategy) {
        javaEeConfiguration = (CommonStrategy)configuration;
      }
    }
    if (javaEeConfiguration == null) return;

    ArtifactManager artifactManager = ArtifactManager.getInstance(project);
    String artifactName = suggestExplodedWarArtifactName(module.getName(), webFacet.getName());

    DeploymentSettings javaEeDeploymentSettings = javaEeConfiguration.getDeploymentSettings();
    if (javaEeDeploymentSettings != null) {
      Artifact warExplodedArtifact = createNewArtifact(gwtFacet, webFacet, artifactManager, artifactName, gwtOutputRelativePath, true);
      javaEeDeploymentSettings.getOrCreateModel(warExplodedArtifact);
      setBuildArtifactBeforeRun(project, javaEeConfiguration, warExplodedArtifact);
    }
    //we need to call javaEeConfiguration.getUrlToOpenInBrowser() after deployment settings are updated to ensure that context root is added to the URL
    javaEeConfiguration.setUrlToOpenInBrowser(DeploymentUtil.appendToPath(javaEeConfiguration.getUrlToOpenInBrowser(), gwtRunConfiguration.getPage()));

    CommonStrategy javaEeDevMode = javaEeConfiguration.clone();
    javaEeDevMode.getSettingsBean().OPEN_IN_BROWSER = false;
    javaEeDevMode.setName(javaEeConfiguration.getName() + " DevMode");

    runManager.addConfiguration(runManager.createConfiguration(javaEeDevMode, javaEeConfiguration.getFactory()));

    DeploymentSettings javaEeDevModeDeploymentSettings = javaEeDevMode.getDeploymentSettings();
    Artifact warExplodedDevModeArtifact = createNewArtifact(gwtFacet, webFacet, artifactManager, artifactName + " DevMode",
                                                            gwtOutputRelativePath, false);
    if (javaEeDevModeDeploymentSettings != null) {
      javaEeDevModeDeploymentSettings.getOrCreateModel(warExplodedDevModeArtifact);

      GwtRunConfiguration noServerConfiguration = (GwtRunConfiguration)gwtRunConfiguration.clone();

      noServerConfiguration.setName(gwtRunConfiguration.getName() + " NoServer");
      @NonNls String programParameters = gwtRunConfiguration.getProgramParameters()
                                         + " -noserver"
                                         + " -port " + javaEeDevMode.getPort()
                                         + " -war " + warExplodedDevModeArtifact.getOutputPath();
      noServerConfiguration.setProgramParameters(programParameters);

      runManager.addConfiguration(runManager.createConfiguration(noServerConfiguration, gwtRunConfiguration.getFactory()));
      setBuildArtifactBeforeRun(project, noServerConfiguration, warExplodedDevModeArtifact);
      setBuildArtifactBeforeRun(project, javaEeDevMode, warExplodedDevModeArtifact);

      CompoundRunConfiguration compoundRunConfiguration = new CompoundRunConfiguration(
        noServerConfiguration.getName() + " with " + javaEeDevMode.getName(), project);
      compoundRunConfiguration.setConfigurationsWithoutTargets(Arrays.asList(noServerConfiguration, javaEeDevMode));
      runManager.addConfiguration(runManager.createConfiguration(compoundRunConfiguration, compoundRunConfiguration.getFactory()));
    }
  }

  /**
   * Same naming scheme as {@code com.intellij.javaee.JavaeeUtil#suggestArtifactName} for Web facets.
   */
  private static @NotNull @NlsSafe String suggestExplodedWarArtifactName(@NotNull String moduleName, @NotNull String webFacetName) {
    return moduleName + ":" + webFacetName + " exploded";
  }

  private static @NotNull Artifact createNewArtifact(@NotNull GwtFacet gwtFacet, @NotNull WebFacet webFacet, @NotNull ArtifactManager artifactManager,
                                                     @NlsSafe String artifactName, @NotNull String gwtOutputRelativePath,
                                                     boolean includeGwtOutput) {
    Module module = gwtFacet.getModule();
    Artifact artifact = artifactManager.addArtifact(artifactName, WebArtifactUtil.getInstance().getExplodedWarArtifactType(), null);

    artifactManager.addElementsToDirectory(artifact, "WEB-INF/classes",
                                           PackagingElementFactory.getInstance().createModuleOutput(module));

    if (includeGwtOutput) {
      artifactManager.addElementsToDirectory(artifact, gwtOutputRelativePath, new GwtCompilerOutputElement(module.getProject(), gwtFacet));
    }

    addServletJarToArtifact(artifactManager, artifact, gwtFacet.getConfiguration().getSdk());

    artifactManager.addElementsToDirectory(artifact, gwtOutputRelativePath,
                                           JavaeeArtifactUtil.getInstance().createFacetResourcesElement(webFacet));
    return artifact;
  }
}

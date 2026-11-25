package com.intellij.gwt.web;

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.gwt.facet.GwtFacet;
import com.intellij.gwt.module.model.GwtModule;
import com.intellij.javaee.DeploymentDescriptorsConstants;
import com.intellij.javaee.web.WebRoot;
import com.intellij.javaee.web.WebUtil;
import com.intellij.javaee.web.artifact.WebArtifactUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.javaee.web.facet.WebFacetConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.descriptors.ConfigFileFactory;
import com.intellij.util.SmartList;
import com.intellij.util.descriptors.ConfigFileMetaData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class GwtWebUtil {
  /** Name of the web application directory in the default GWT project layout. */
  public static final @NonNls String DEFAULT_WAR_DIRECTORY_NAME = "war";
  private static final @NonNls List<String> DEFAULT_WEB_ROOT_PATHS = List.of(DEFAULT_WAR_DIRECTORY_NAME, "src/main/webapp");

  private GwtWebUtil() {
  }

  /**
   * The Web facet type and the web-related services are provided by the JavaEE Web plugin, which requires an
   * IntelliJ IDEA Ultimate subscription. Without it, no Web facets exist and the web.xml-based features are inactive.
   */
  public static boolean isWebFacetTypeAvailable() {
    return FacetTypeRegistry.getInstance().findFacetType(WebFacet.ID) != null;
  }

  /**
   * @return the {@link WebArtifactUtil} service, or {@code null} if the JavaEE Web plugin providing it is not loaded
   */
  public static @Nullable WebArtifactUtil getWebArtifactUtil() {
    return ApplicationManager.getApplication().getService(WebArtifactUtil.class);
  }

  /**
   * @return the created Web facet, or {@code null} if the Web facet type is not available (see {@link #isWebFacetTypeAvailable()})
   */
  public static @Nullable WebFacet createWebFacet(@NotNull GwtFacet facet, @NotNull VirtualFile root) {
    final FacetType<WebFacet, WebFacetConfiguration> webFacetType = FacetTypeRegistry.getInstance().findFacetType(WebFacet.ID);
    if (webFacetType == null) return null;
    Project project = facet.getModule().getProject();
    final ConfigFileMetaData webXml = DeploymentDescriptorsConstants.WEB_XML_META_DATA;
    final @NonNls String webRootUrl = root.getUrl() + "/" + DEFAULT_WAR_DIRECTORY_NAME;
    final String webXmlUrl = webRootUrl + "/" + webXml.getDirectoryPath() + "/" + webXml.getFileName();
    final WebFacet webFacet = FacetManager.getInstance(facet.getModule()).addFacet(webFacetType, webFacetType.getDefaultFacetName(), null);
    webFacet.addWebRoot(webRootUrl, "/");
    ConfigFileFactory.getInstance().createFile(project, webXmlUrl, webXml.getDefaultVersion(), false);
    webFacet.getDescriptorsContainer().getConfiguration().replaceConfigFile(webXml, webXmlUrl);
    facet.getConfiguration().setWebFacetName(webFacet.getName());
    return webFacet;
  }

  public static @NotNull @NlsSafe String getOutputPath(@NotNull GwtModule gwtModule, @NotNull String relativePath) {
    return gwtModule.getOutputName() + "/" + relativePath;
  }

  public static @Nullable @NlsSafe String getRelativeToWebRootPath(@NotNull VirtualFile htmlFile, @NotNull Project project) {
    final WebFacet webFacet = WebUtil.findFacetByFileUnderWebRoot(htmlFile, project);
    if (webFacet != null) {
      final WebRoot webRoot = WebUtil.findParentWebRoot(htmlFile, webFacet.getWebRoots());
      if (webRoot != null) {
        final VirtualFile rootFile = webRoot.getFile();
        if (rootFile != null) {
          return VfsUtilCore.getRelativePath(htmlFile, rootFile, '/');
        }
      }
    }
    else if (!isWebFacetTypeAvailable()) {
      final Module module = ModuleUtilCore.findModuleForFile(htmlFile, project);
      if (module != null) {
        for (VirtualFile rootFile : findDefaultWebRoots(module)) {
          if (VfsUtilCore.isAncestor(rootFile, htmlFile, true)) {
            return VfsUtilCore.getRelativePath(htmlFile, rootFile, '/');
          }
        }
      }
    }
    return null;
  }

  /**
   * Web application directories of the conventional GWT ({@code war}) and Maven ({@code src/main/webapp}) project layouts.
   * Used instead of the Web facet's web roots when the Web facet type is not available (see {@link #isWebFacetTypeAvailable()}).
   */
  public static @NotNull List<VirtualFile> findDefaultWebRoots(@NotNull Module module) {
    final List<VirtualFile> result = new SmartList<>();
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      for (String relativePath : DEFAULT_WEB_ROOT_PATHS) {
        final VirtualFile dir = contentRoot.findFileByRelativePath(relativePath);
        if (dir != null && dir.isDirectory()) {
          result.add(dir);
        }
      }
    }
    return result;
  }
}

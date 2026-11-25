// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gwt.facet;

import com.intellij.gwt.run.GwtRunConfiguration;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sets up additional run configurations and artifacts for a newly created GWT sample application.
 * <p>
 * The implementation for Java EE application servers lives in the optional {@code intellij.gwt.javaee} content module,
 * because the application server integration requires an IntelliJ IDEA Ultimate subscription.
 */
@ApiStatus.Internal
public interface GwtSampleApplicationRunConfigurator {
  ExtensionPointName<GwtSampleApplicationRunConfigurator> EP_NAME =
    ExtensionPointName.create("com.intellij.gwt.sampleApplicationRunConfigurator");

  /**
   * Called inside a write command after the GWT run configuration of the sample application was created.
   *
   * @param gwtOutputRelativePath path inside the web application where the GWT compiler output should be placed
   */
  void configure(@NotNull GwtFacet gwtFacet,
                 @Nullable WebFacet webFacet,
                 @NotNull GwtRunConfiguration gwtRunConfiguration,
                 @NotNull String gwtOutputRelativePath);
}

/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2018-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.repository.helm;

import org.apache.commons.lang3.StringUtils;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.rest.UploadDefinitionExtension;
import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.VariableResolverAdapter;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.TempBlob;
import org.sonatype.nexus.repository.transaction.TransactionalStoreBlob;
import org.sonatype.nexus.repository.upload.ComponentUpload;
import org.sonatype.nexus.repository.upload.UploadDefinition;
import org.sonatype.nexus.repository.upload.UploadHandlerSupport;
import org.sonatype.nexus.repository.upload.UploadResponse;
import org.sonatype.nexus.repository.view.PartPayload;
import org.sonatype.nexus.rest.ValidationErrorsException;
import org.sonatype.repository.helm.internal.AssetKind;
import org.sonatype.repository.helm.internal.HelmFormat;
import org.sonatype.repository.helm.internal.hosted.HelmHostedFacet;
import org.sonatype.repository.helm.internal.util.HelmAttributeParser;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.sonatype.repository.helm.internal.util.HelmDataAccess.HASH_ALGORITHMS;
import static org.sonatype.repository.helm.internal.util.HelmPathUtils.PROVENANCE_EXTENSION;
import static org.sonatype.repository.helm.internal.util.HelmPathUtils.TGZ_EXTENSION;

/**
 * Support helm upload for web page
 *
 * @author yinlongfei
 */
@Singleton
@Named(HelmFormat.NAME)
public class HelmUploadHandler
    extends UploadHandlerSupport
{
  private UploadDefinition definition;

  private final ContentPermissionChecker contentPermissionChecker;

  private final VariableResolverAdapter variableResolverAdapter;

  private final HelmAttributeParser helmPackageParser;


  @Inject
  public HelmUploadHandler(final ContentPermissionChecker contentPermissionChecker,
                           final HelmAttributeParser helmPackageParser,
                           @Named("simple") final VariableResolverAdapter variableResolverAdapter,
                           final Set<UploadDefinitionExtension> uploadDefinitionExtensions)
  {
    super(uploadDefinitionExtensions);
    this.contentPermissionChecker = contentPermissionChecker;
    this.variableResolverAdapter = variableResolverAdapter;
    this.helmPackageParser = helmPackageParser;
  }

  @Override
  public UploadResponse handle(Repository repository, ComponentUpload upload) throws IOException {
    HelmHostedFacet facet = repository.facet(HelmHostedFacet.class);
    StorageFacet storageFacet = repository.facet(StorageFacet.class);

    PartPayload payload = upload.getAssetUploads().get(0).getPayload();

    String extension = Optional
        .ofNullable(payload.getName())
        .map(name -> name.substring(name.lastIndexOf(".")))
        .orElse(StringUtils.EMPTY);

    try (TempBlob tempBlob = storageFacet.createTempBlob(payload, HASH_ALGORITHMS)) {
      HelmAttributes attributesFromInputStream;
      AssetKind assetKind;

      if (extension.equals(PROVENANCE_EXTENSION)) {
        attributesFromInputStream = helmPackageParser.getAttributesProvenanceFromInputStream(tempBlob.get());
        assetKind = AssetKind.HELM_PROVENANCE;
      } else if (extension.equals(TGZ_EXTENSION)) {
        attributesFromInputStream = helmPackageParser.getAttributesFromInputStream(tempBlob.get());
        assetKind = AssetKind.HELM_PACKAGE;
      } else {
        throw new IllegalArgumentException("Unsupported extension: " + extension);
      }

      String name = attributesFromInputStream.getName();
      String version = attributesFromInputStream.getVersion();

      if (StringUtils.isBlank(name)) {
        throw new ValidationErrorsException("Metadata is missing the name attribute");
      }

      if (StringUtils.isBlank(version)) {
        throw new ValidationErrorsException("Metadata is missing the version attribute");
      }

      String path = String.format("%s-%s%s", name, version, extension);

      ensurePermitted(repository.getName(), HelmFormat.NAME, path, Collections.emptyMap());
      return TransactionalStoreBlob.operation.withDb(storageFacet.txSupplier()).throwing(IOException.class)
          .call(() -> new UploadResponse(facet.upload(path, tempBlob, payload, assetKind)));
    }
  }


  @Override
  public UploadDefinition getDefinition() {
    if (definition == null) {
      definition = getDefinition(HelmFormat.NAME, false);
    }
    return definition;
  }

  @Override
  public VariableResolverAdapter getVariableResolverAdapter() {
    return variableResolverAdapter;
  }

  @Override
  public ContentPermissionChecker contentPermissionChecker() {
    return contentPermissionChecker;
  }
}

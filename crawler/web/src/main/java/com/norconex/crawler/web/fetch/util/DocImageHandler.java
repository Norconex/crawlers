/* Copyright 2019-2024 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.web.fetch.util;

import java.io.File;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.DirStructure;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.Target;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.transformer.impl.ImageTransformer;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Handles images associated with a document (which is different than a document
 * being itself an image).  Examples can be screenshots, featured image, etc.
 * Images can be stored in a document metadata/field or in a local directory.
 * </p>
 * @since 3.0.0
 */
@Slf4j
@ToString
@EqualsAndHashCode
public class DocImageHandler implements Configurable<DocImageHandlerConfig> {

    @Getter
    @Setter
    @NonNull
    private DocImageHandlerConfig configuration = new DocImageHandlerConfig();

    private final ImageTransformer imgTransformer = new ImageTransformer();

    public void handleImage(InputStream imageStream, Doc doc) {

        //TODO check for null and:
        //  1. apply defaults?  2, log error?  3. throw error?

        try {
            var format = Optional.ofNullable(
                    configuration.getImageFormat()).orElse(
                            DocImageHandlerConfig.DEFAULT_IMAGE_FORMAT);
            var img = new MutableImage(imageStream);
            imgTransformer.transformImage(img);

            if (configuration.getTargets().contains(Target.METADATA)) {
                Objects.requireNonNull(
                        configuration.getTargetMetaField(),
                        "'targetMetaField'' must not be null");
                doc.getMetadata().add(
                        configuration.getTargetMetaField(),
                        img.toBase64String(format));
            }
            if (configuration.getTargets().contains(Target.DIRECTORY)) {
                Objects.requireNonNull(
                        configuration.getTargetDirField(),
                        "'targetDirField'' must not be null");
                Objects.requireNonNull(
                        configuration.getTargetDir(),
                        "'targetDir'' must not be null");
                var dir = configuration.getTargetDir().toFile();
                var ref = doc.getReference();
                var ext = "." + format;
                File imageFile = null;
                if (configuration
                        .getTargetDirStructure() == DirStructure.URL2PATH) {
                    imageFile = new File(
                            FileUtil.createURLDirs(
                                    dir, ref, true).getAbsolutePath() + ext);
                } else if (configuration
                        .getTargetDirStructure() == DirStructure.DATE) {
                    imageFile = new File(
                            FileUtil.createDateDirs(dir),
                            TimeIdGenerator.next() + ext);
                } else { // DATETIME (Default)
                    imageFile = new File(
                            FileUtil.createDateTimeDirs(dir),
                            TimeIdGenerator.next() + ext);
                }
                img.write(imageFile.toPath(), format);
                doc.getMetadata().add(
                        configuration.getTargetDirField(),
                        imageFile.getCanonicalPath());
            }
        } catch (Exception e) {
            LOG.error("Could not handle image for: {}", doc.getReference(), e);
        }
    }
}

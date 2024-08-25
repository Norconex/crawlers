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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Handles images associated with a document (which is different than a document
 * being itself an image).  Examples can be screenshots, featured image, etc.
 * Images can be stored in a document metadata/field or in a local directory.
 * </p>
 *
 * {@nx.xml.usage
 * <targets>[metadata|directory] (One or both, separated by comma.)</targets>
 * <imageFormat>(Image format. Default is "png".)</imageFormat>
 * <!-- The following applies to the "directory" target: -->
 * <targetDir
 *     field="(Document field to store the local path to the image.)"
 *     structure="[url2path|date|datetime]">
 *   (Local directory where to save images.)
 * </targetDir>
 * <!-- The following applies to the "metadata" target: -->
 * <targetMetaField>(Document field where to store the image.)</targetMetaField>
 * }
 * <p>
 * The above XML configurable options can be nested in a parent tag of any name.
 * The expected parent tag name is defined by the consuming classes.
 * </p>
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class DocImageHandlerConfig {

    public enum Target {
        METADATA, DIRECTORY
    }

    public enum DirStructure {
        URL2PATH, DATE, DATETIME
    }

    public static final String DEFAULT_IMAGE_FORMAT = "png";

    protected static final List<Target> DEFAULT_TYPES =
            List.of(Target.DIRECTORY);

    //    @ToString.Exclude
    //    @EqualsAndHashCode.Exclude
    private final List<Target> targets = new ArrayList<>(DEFAULT_TYPES);
    private Path targetDir;
    private String targetDirField;
    private DirStructure targetDirStructure = DirStructure.DATETIME;
    private String targetMetaField;
    private String imageFormat = DEFAULT_IMAGE_FORMAT;
    //    private final ImageTransformer imgTransformer = new ImageTransformer();

    public List<Target> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    public DocImageHandlerConfig setTargets(List<Target> targets) {
        CollectionUtil.setAll(this.targets, targets);
        return this;
    }

    //
    //    @Override
    //    public void loadFromXML(XML xml) {
    //        setTargets(xml.getDelimitedEnumList("targets", Target.class, targets));
    //        setTargetDir(xml.getPath("targetDir", targetDir));
    //        setTargetDirStructure(xml.getEnum("targetDir/@structure",
    //                DirStructure.class, targetDirStructure));
    //        setTargetDirField(xml.getString("targetDir/@field", targetDirField));
    //        setTargetMetaField(xml.getString("targetMetaField", targetMetaField));
    //        setImageFormat(xml.getString("imageFormat", imageFormat));
    //    }
    //
    //    @Override
    //    public void saveToXML(XML xml) {
    //        xml.addDelimitedElementList("targets", targets);
    //        xml.addElement("targetDir", targetDir)
    //                .setAttribute("structure", targetDirStructure)
    //                .setAttribute("field", targetDirField);
    //        xml.addElement("targetMetaField", targetMetaField);
    //        xml.addElement("imageFormat", imageFormat);
    //    }
}

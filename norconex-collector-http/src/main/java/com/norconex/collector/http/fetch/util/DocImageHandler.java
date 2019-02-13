package com.norconex.collector.http.fetch.util;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.importer.handler.transformer.impl.ImageTransformer;

/**
 * Handles images associated with a document (which is different than a document
 * being itself an image.  Examples can be screenshots, featured image, etc.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class DocImageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(
            DocImageHandler.class);

    public enum Target { METADATA, DIRECTORY }
    public enum DirStructure { URL2PATH, DATE, DATETIME }
    public static final String DEFAULT_IMAGE_FORMAT = "png";

//    public static final String DEFAULT_DISK_DIR = "./media";
    public static final List<Target> DEFAULT_TYPES =
            Arrays.asList(Target.DIRECTORY) ;

    private final List<Target> targets = new ArrayList<>(DEFAULT_TYPES);
    private Path targetDir;
    private String targetDirField;
    private DirStructure targetDirStructure = DirStructure.DATETIME;
    private String targetMetaField;
    private String imageFormat = DEFAULT_IMAGE_FORMAT;


    private final ImageTransformer imgTransformer = new ImageTransformer();

    public DocImageHandler(
            Path defaultDir,
            String defaultDirField,
            String defaultMetaField) {
        super();
        this.targetDir = defaultDir;
        this.targetDirField = defaultDirField;
        this.targetMetaField = defaultMetaField;
    }

    public DocImageHandler() {
        super();
    }

    public Path getTargetDir() {
        return targetDir;
    }
    public void setTargetDir(Path diskDir) {
        this.targetDir = diskDir;
    }

    public String getTargetDirField() {
        return targetDirField;
    }
    public void setTargetDirField(String diskField) {
        this.targetDirField = diskField;
    }

    public DirStructure getTargetDirStructure() {
        return targetDirStructure;
    }
    public void setTargetDirStructure(DirStructure dirStructure) {
        this.targetDirStructure = dirStructure;
    }

    public String getTargetMetaField() {
        return targetMetaField;
    }
    public void setTargetMetaField(String metadataField) {
        this.targetMetaField = metadataField;
    }

    public List<Target> getTargets() {
        return Collections.unmodifiableList(targets);
    }
    public void setTargets(Target... targets) {
        setTargets(Arrays.asList(targets));
    }
    public void setTargets(List<Target> targets) {
        CollectionUtil.setAll(this.targets, targets);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }

    public void handleImage(InputStream imageStream, HttpDocument doc) {
        //if (!screenshotEnabled) {
        //    return;
        //}

        //TODO check for null and:
        //  1. apply defaults?  2, log error?  3. throw error?

        try {
            MutableImage img = new MutableImage(imageStream);

            imgTransformer.transformImage(img);

            if (targets.contains(Target.METADATA)) {
                doc.getMetadata().add(
                        targetMetaField, img.toBase64String(imageFormat));
            }
            if (targets.contains(Target.DIRECTORY)) {
                File dir = targetDir.toFile();
                String ref = doc.getReference();
                String ext = "." + imageFormat;
                File imageFile = null;
                if (targetDirStructure == DirStructure.URL2PATH) {
                    imageFile = new File(FileUtil.createURLDirs(
                            dir, ref, true).getAbsolutePath() + ext);
                } else if (targetDirStructure == DirStructure.DATE) {
                    imageFile = new File(FileUtil.createDateDirs(dir),
                            TimeIdGenerator.next() + ext);
                } else { // DATETIME
                    imageFile = new File(FileUtil.createDateTimeDirs(dir),
                            TimeIdGenerator.next() + ext);
                }
                img.write(imageFile.toPath(), imageFormat);
                doc.getMetadata().add(
                        targetDirField, imageFile.getCanonicalPath());
            }
        } catch (Exception e) {
            LOG.error("Could not take screenshot of: {}",
                    doc.getReference(), e);
        }
    }
}

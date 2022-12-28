/* Copyright 2019-2022 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import com.norconex.commons.lang.convert.DimensionConverter;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.ExternalHandler;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.AbstractDocumentTransformer;
import com.norconex.importer.parser.GenericDocumentParserFactory;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Transforms an image using common image operations.
 * </p>
 * <p>
 * This class should only be used as a pre-parsing handler, on image files.
 * It may also be appropriate to disable parsing of those images if you
 * want to keep the transformed version intact. This can be done with
 * {@link GenericDocumentParserFactory}.
 * </p>
 *
 * <h3>Content-types</h3>
 * <p>
 * By default, this filter is restricted to (applies only to) documents matching
 * the restrictions returned by
 * {@link CommonRestrictions#imageIOStandardContentTypes(String)}.
 * You can specify your own content types if you know they represent a supported
 * image.
 * </p>
 *
 * <h3>Image dimension format</h3>
 * <p>
 * For a list of supported image dimension formats, refer to
 * {@link DimensionConverter}.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.ImageTransformer"
 *      targetFormat="(jpg, png, gif, bmp, wbmp, or other supported format)">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <scale
 *       stretch="[false|true]"
 *       factor="(decimal value ratio factor, default is 1)"
 *       dimension="(target dimension, in pixels, format: [width]x[height])" />
 *
 *   <rotate degrees="(-360 to 360)"/>
 *
 *   <crop
 *       x="(top-left x-axis, default 0)"
 *       y="(top-left y-axis, default 0)"
 *       dimension="(crop dimension, in pixels, format: [width]x[height])"/>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="ImageTransformer" targetFormat="png">
 *   <scale dimension="400x250" />
 * </handler>
 * }
 * <h4>Usage example:</h4>
 * <p>
 * The above example converts images to PNG while scaling it to a maximum
 * dimension of 400 pixels wide and 250 pixel high.
 * </p>
 *
 * @see ExternalHandler
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class ImageTransformer extends AbstractDocumentTransformer {

    public static final String DEFAULT_TARGET_FORMAT = "png";

    //TODO Maybe: default == null == keep same as source,
    // derived from detected content-type
    private String targetFormat = DEFAULT_TARGET_FORMAT;

    private boolean scaleStretch;
    private Double scaleFactor;
    private Dimension scaleDimension;
    private Double rotateDegrees;
    private Rectangle cropRectangle;

    public ImageTransformer() {
        addRestrictions(CommonRestrictions.imageIOStandardContentTypes(
                DocMetadata.CONTENT_TYPE));
    }

    public String getTargetFormat() {
        return targetFormat;
    }
    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public boolean isScaleStretch() {
        return scaleStretch;
    }
    public void setScaleStretch(boolean scaleStretch) {
        this.scaleStretch = scaleStretch;
    }

    public Double getScaleFactor() {
        return scaleFactor;
    }
    public void setScaleFactor(Double scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public Dimension getScaleDimension() {
        return scaleDimension;
    }
    public void setScaleDimension(Dimension scaleDimension) {
        this.scaleDimension = scaleDimension;
    }

    public Double getRotateDegrees() {
        return rotateDegrees;
    }
    public void setRotateDegrees(Double rotateDegrees) {
        this.rotateDegrees = rotateDegrees;
    }

    public Rectangle getCropRectangle() {
        return cropRectangle;
    }
    public void setCropRectangle(Rectangle cropRectangle) {
        this.cropRectangle = cropRectangle;
    }

    @Override
    protected void transformApplicableDocument(
            HandlerDoc doc, final InputStream input, final OutputStream output,
            final ParseState parseState) throws ImporterHandlerException {
        Objects.requireNonNull("'targetFormat' must not be null");

        try {
            var img = new MutableImage(input);
            transformImage(img);
            img.write(output, targetFormat);
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Could not transform image: " + doc.getReference(), e);
        }
    }

    public void transformImage(MutableImage image) {
        // Scale
        if (scaleFactor != null) {
            image.scaleFactor(scaleFactor);
        }
        if (scaleDimension != null) {
            if (scaleStretch) {
                image.stretch(scaleDimension);
            } else {
                image.scale(scaleDimension);
            }
        }

        // Rotate
        if (rotateDegrees != null) {
            image.rotate(rotateDegrees);
        }

        // Crop
        if (cropRectangle != null) {
            image.crop(cropRectangle);
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setTargetFormat(xml.getString("@targetFormat", targetFormat));
        setScaleStretch(xml.getBoolean("scale/@stretch", scaleStretch));
        setScaleFactor(xml.getDouble("scale/@factor", scaleFactor));
        setScaleDimension(xml.getDimension("scale/@dimension", scaleDimension));
        setRotateDegrees(xml.getDouble("rotate/@degrees", rotateDegrees));
        setTargetFormat(xml.getString("@targetFormat", targetFormat));
        if (xml.contains("crop/@dimension")) {
            var dim = xml.getDimension("crop/@dimension");
            setCropRectangle(new Rectangle(
                    xml.getInteger("crop/@x", 0), xml.getInteger("crop/@y", 0),
                    dim.width, dim.height));
        }
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("targetFormat", targetFormat);
        xml.addElement("scale")
                .setAttribute("stretch", scaleStretch)
                .setAttribute("factor", scaleFactor)
                .setAttribute("dimension", scaleDimension);
        xml.addElement("rotate")
                .setAttribute("degrees", rotateDegrees);
        if (cropRectangle != null) {
            xml.addElement("crop")
                    .setAttribute("x", cropRectangle.x)
                    .setAttribute("y", cropRectangle.y)
                    .setAttribute("dimension",
                            cropRectangle.width + "x" + cropRectangle.height);
        }
    }
}
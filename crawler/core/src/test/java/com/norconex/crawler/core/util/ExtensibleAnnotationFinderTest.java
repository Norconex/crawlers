/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class ExtensibleAnnotationFinderTest {

    // -----------------------------------------------------------------
    // Test annotations
    // -----------------------------------------------------------------

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface BaseAnnotation {
        String value() default "base";

        String extra() default "extra-default";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @BaseAnnotation(value = "meta-value")
    @interface MetaAnnotation {
        // extends BaseAnnotation via meta-annotation
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @BaseAnnotation(value = "child-value", extra = "child-extra")
    @interface ChildAnnotation {
        // overrides base via meta
    }

    // -----------------------------------------------------------------
    // Test classes / methods
    // -----------------------------------------------------------------

    @BaseAnnotation(value = "direct")
    static class DirectlyAnnotated {
    }

    @MetaAnnotation
    static class MetaAnnotated {
    }

    static class NotAnnotated {
    }

    static class ParentAnnotated {
        @BaseAnnotation(value = "parent-method")
        public void theMethod() {
        }
    }

    // Annotation without special meaning, just to trigger super-method search
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface OtherAnnotation {
    }

    static class ChildClass extends ParentAnnotated {
        @OtherAnnotation // has an annotation → triggers super-method search
        @Override
        public void theMethod() {
            // the @BaseAnnotation should be found on the parent method
        }
    }

    @OtherAnnotation // has an annotation → triggers super-class search
    static class ImplementsAnnotated implements AnnotatedInterface {
    }

    @BaseAnnotation(value = "iface-val")
    interface AnnotatedInterface {
    }

    // -----------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------

    @Test
    void testFind_nullElementReturnsEmpty() {
        var result =
                ExtensibleAnnotationFinder.find(null, BaseAnnotation.class);
        assertThat(result).isEmpty();
    }

    @Test
    void testFind_directAnnotation() {
        var result = ExtensibleAnnotationFinder.find(
                DirectlyAnnotated.class, BaseAnnotation.class);
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("direct");
    }

    @Test
    void testFind_notAnnotated_returnsEmpty() {
        var result = ExtensibleAnnotationFinder.find(
                NotAnnotated.class, BaseAnnotation.class);
        assertThat(result).isEmpty();
    }

    @Test
    void testFind_viaMetaAnnotation() {
        var result = ExtensibleAnnotationFinder.find(
                MetaAnnotated.class, BaseAnnotation.class);
        assertThat(result).isPresent();
        // the meta-annotation holds value = "meta-value"
        assertThat(result.get().value()).isEqualTo("meta-value");
    }

    @Test
    void testFind_annotationOnMethod() throws NoSuchMethodException {
        Method method = ParentAnnotated.class.getMethod("theMethod");
        var result = ExtensibleAnnotationFinder.find(
                method, BaseAnnotation.class);
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("parent-method");
    }

    @Test
    void testFind_onChildClassMethod_inheritsFromParent()
            throws NoSuchMethodException {
        Method method = ChildClass.class.getMethod("theMethod");
        var result = ExtensibleAnnotationFinder.find(
                method, BaseAnnotation.class);
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("parent-method");
    }

    @Test
    void testFind_onClassImplementingAnnotatedInterface() {
        var result = ExtensibleAnnotationFinder.find(
                ImplementsAnnotated.class, BaseAnnotation.class);
        // The annotation is on the interface, which should be found in super-class search
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("iface-val");
    }
}

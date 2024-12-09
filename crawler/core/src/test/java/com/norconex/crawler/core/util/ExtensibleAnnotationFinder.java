/* Copyright 2024 Norconex Inc.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public final class ExtensibleAnnotationFinder {

    private ExtensibleAnnotationFinder() {
    }

    @SuppressWarnings("unchecked")
    public static <A extends Annotation> Optional<A> find(
            AnnotatedElement element, @NonNull Class<A> target) {
        if (element == null) {
            return Optional.empty();
        }

        AnnotPair foundAnnotPair = null;

        for (var annot : element.getAnnotations()) {
            var result = find(element, annot, (Class<Annotation>) target);
            if (result != null) {
                foundAnnotPair = result;
                break;
            }
        }

        if (foundAnnotPair == null) {
            return Optional.empty();
        }

        if (foundAnnotPair.extension == null) {
            return (Optional<A>) Optional.ofNullable(foundAnnotPair.base);
        }

        Map<String, Object> mergedAttributes = new HashMap<>();

        // Get values from base annotation
        describe(mergedAttributes, foundAnnotPair.base);
        // Override with values from the extending annotation
        describe(mergedAttributes, foundAnnotPair.extension);

        return (Optional<A>) Optional
                .ofNullable((Annotation) Proxy.newProxyInstance(
                        target.getClassLoader(),
                        new Class[] { target },
                        new MergedAnnotationInvocationHandler(
                                target, mergedAttributes)));
    }

    private static void describe(Map<String, Object> props, Annotation annot) {
        for (Method method : annot.annotationType().getDeclaredMethods()) {
            try {
                var value = method.invoke(annot);
                props.put(method.getName(), value);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to read attribute from annotation", e);
            }
        }
    }

    private static AnnotPair find(
            AnnotatedElement element,
            Annotation annotation,
            Class<Annotation> target) {

        var pair = asPair(annotation, target);
        if (pair != null) {
            return pair;
        }

        // check meta annotations
        var metaAnnot = findMeta(annotation, target);
        if (metaAnnot != null) {
            return metaAnnot;
        }

        // check super classes/interfaces
        if (element instanceof Method method) {
            return findOnSuperMethod(method, target);
        }
        if (element instanceof Class<?> cls) {
            return findOnSuperClass(cls, target);
        }
        return null;
    }

    private static AnnotPair asPair(
            Annotation annotation, Class<Annotation> target) {
        if (target.isAssignableFrom(annotation.annotationType())) {
            return new AnnotPair(annotation);
        }
        var annot = annotation.annotationType().getAnnotation(target);
        if (annot != null) {
            return new AnnotPair(annot, annotation);
        }
        return null;
        //        return findMeta(annot, target);
    }

    private static AnnotPair findMeta(
            Annotation annotation, Class<Annotation> markerTarget) {
        return findMeta(annotation, markerTarget, new HashSet<>());
    }

    private static AnnotPair findMeta(
            Annotation annotation,
            Class<Annotation> target,
            Set<Object> uniqueSet) {
        var annotType = annotation.annotationType();
        for (Annotation metaAnnot : annotType.getAnnotations()) {
            if (!metaAnnot.equals(annotation) && uniqueSet.add(metaAnnot)) {
                var pair = asPair(metaAnnot, target);
                if (pair != null) {
                    return pair;
                }
            }
        }
        return null;
    }

    private static AnnotPair findOnSuperMethod(
            Method method, Class<Annotation> target) {

        Class<?> currentClass = method.getDeclaringClass();
        while (currentClass != null) {
            // interfaces
            for (Class<?> iface : currentClass.getInterfaces()) {
                var annot = doFindOnSuperMethod(
                        superMethod(iface, method, false), target);
                if (annot != null) {
                    return annot;
                }
            }

            // superclass
            Class<?> superClass = currentClass.getSuperclass();
            if (superClass != currentClass) {
                var annot = doFindOnSuperMethod(
                        superMethod(superClass, method, true), target);
                if (annot != null) {
                    return annot;
                }
            }
            // Move up to the superclass
            currentClass = superClass;
        }
        return null;
    }

    private static AnnotPair doFindOnSuperMethod(
            Method superMethod, Class<Annotation> target) {
        if (superMethod != null) {
            for (var annot : superMethod.getAnnotations()) {
                var pair = asPair(annot, target);
                if (pair != null) {
                    return pair;
                }
            }
        }
        return null;
    }

    private static AnnotPair findOnSuperClass(
            Class<?> cls, Class<Annotation> target) {

        Class<?> currentClass = cls;
        while (currentClass != null) {
            // interfaces
            for (Class<?> iface : currentClass.getInterfaces()) {
                for (var annot : iface.getAnnotations()) {
                    var pair = asPair(annot, target);
                    if (pair != null) {
                        return pair;
                    }
                }
            }

            Class<?> superClass = currentClass.getSuperclass();
            if (superClass != currentClass) {
                for (var annot : currentClass.getAnnotations()) {
                    var pair = asPair(annot, target);
                    if (pair != null) {
                        return pair;
                    }
                }
            }
            // Move up to the superclass
            currentClass = superClass;
        }
        return null;
    }

    /**
     * Invocation handler for the merged annotation proxy.
     */
    private static class MergedAnnotationInvocationHandler
            implements InvocationHandler {
        private final Class<? extends Annotation> annotationType;
        private final Map<String, Object> attributes;

        public MergedAnnotationInvocationHandler(
                Class<? extends Annotation> annotationType,
                Map<String, Object> attributes) {
            this.annotationType = annotationType;
            this.attributes = attributes;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            var methodName = method.getName();
            if ("annotationType".equals(methodName)) {
                return annotationType;
            }
            return attributes.get(methodName);
        }
    }

    private static Method superMethod(
            Class<?> superClass, Method subMethod, boolean declared) {
        try {
            if (declared) {
                return superClass.getDeclaredMethod(
                        subMethod.getName(),
                        subMethod.getParameterTypes());
            }
            return superClass.getMethod(
                    subMethod.getName(),
                    subMethod.getParameterTypes());
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    @Data
    @RequiredArgsConstructor
    private static class AnnotPair {
        private final Annotation base;
        private final Annotation extension;

        AnnotPair(Annotation base) {
            this.base = base;
            extension = null;
        }
    }
}

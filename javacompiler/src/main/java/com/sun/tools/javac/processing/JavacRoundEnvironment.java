/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.tools.javac.processing;

import java.lang.annotation.Annotation;
import com.sun.tools.javac.tree.JCTree.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.*;
import java.util.*;

/**
 * Object providing state about a prior round of annotation processing.
 *
 * <p>The methods in this class do not take type annotations into account,
 * as target types, not java elements.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacRoundEnvironment implements RoundEnvironment {
    // Default equals and hashCode methods are okay.

    private final boolean processingOver;
    private final boolean errorRaised;
    private final ProcessingEnvironment processingEnv;

    // Caller must pass in an immutable set
    private final Set<? extends Element> rootElements;

    JavacRoundEnvironment(boolean processingOver,
                          boolean errorRaised,
                          Set<? extends Element> rootElements,
                          ProcessingEnvironment processingEnv) {
        this.processingOver = processingOver;
        this.errorRaised = errorRaised;
        this.rootElements = rootElements;
        this.processingEnv = processingEnv;
    }

    public String toString() {
        return String.format("[errorRaised=%b, rootElements=%s, processingOver=%b]",
                             errorRaised,
                             rootElements,
                             processingOver);
    }

    public boolean processingOver() {
        return processingOver;
    }

    /**
     * Returns {@code true} if an error was raised in the prior round
     * of processing; returns {@code false} otherwise.
     *
     * @return {@code true} if an error was raised in the prior round
     * of processing; returns {@code false} otherwise.
     */
    public boolean errorRaised() {
        return errorRaised;
    }

    /**
     * Returns the type elements specified by the prior round.
     *
     * @return the types elements specified by the prior round, or an
     * empty set if there were none
     */
    public Set<? extends Element> getRootElements() {
        return rootElements;
    }

    private static final String NOT_AN_ANNOTATION_TYPE =
        "The argument does not represent an annotation type: ";

    /**
     * Returns the elements annotated with the given annotation type.
     * Only type elements <i>included</i> in this round of annotation
     * processing, or declarations of members, parameters, or type
     * parameters declared within those, are returned.  Included type
     * elements are {@linkplain #getSpecifiedTypeElements specified
     * types} and any types nested within them.
     *
     * @param a  annotation type being requested
     * @return the elements annotated with the given annotation type,
     * or an empty set if there are none
     */
    public Set<? extends Element> getElementsAnnotatedWith(TypeElement a) {
        Set<Element> result = Collections.emptySet();
        Types typeUtil = processingEnv.getTypeUtils();
        if (a.getKind() != ElementKind.ANNOTATION_TYPE)
            throw new IllegalArgumentException(NOT_AN_ANNOTATION_TYPE + a);

        DeclaredType annotationTypeElement;
        TypeMirror tm = a.asType();
        if ( tm instanceof DeclaredType )
            annotationTypeElement = (DeclaredType) a.asType();
        else
            throw new AssertionError("Bad implementation type for " + tm);

        ElementScanner7<Set<Element>, DeclaredType> scanner =
            new AnnotationSetScanner(result, typeUtil);

        for (Element element : rootElements)
            result = scanner.scan(element, annotationTypeElement);

        return result;
    }

    // Could be written as a local class inside getElementsAnnotatedWith
    private class AnnotationSetScanner extends
        ElementScanner7<Set<Element>, DeclaredType> {
        // Insertion-order preserving set
        Set<Element> annotatedElements = new LinkedHashSet<Element>();
        Types typeUtil;

        AnnotationSetScanner(Set<Element> defaultSet, Types typeUtil) {
            super(defaultSet);
            this.typeUtil = typeUtil;
        }

        @Override
        public Set<Element> scan(Element e, DeclaredType p) {
            List<? extends AnnotationMirror> annotationMirrors =
                processingEnv.getElementUtils().getAllAnnotationMirrors(e);
            for (AnnotationMirror annotationMirror : annotationMirrors) {
                if (typeUtil.isSameType(annotationMirror.getAnnotationType(), p))
                    annotatedElements.add(e);
            }
            e.accept(this, p);
            return annotatedElements;
        }

    }

    /**
     * {@inheritdoc}
     */
    public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> a) {
        if (!a.isAnnotation())
            throw new IllegalArgumentException(NOT_AN_ANNOTATION_TYPE + a);
        String name = a.getCanonicalName();
        if (name == null)
            return Collections.emptySet();
        else {
            TypeElement annotationType = processingEnv.getElementUtils().getTypeElement(name);
            if (annotationType == null)
                return Collections.emptySet();
            else
                return getElementsAnnotatedWith(annotationType);
        }
    }
}

/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.compiler.injection;

import grails.artefact.Artefact;

import java.util.ArrayList;
import java.util.List;

import grails.build.logging.GrailsConsole;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * A transformation used to apply transformers to classes not located in Grails
 * directory structure. For example any class can be annotated with
 * &#064;Artefact("Controller") to make it into a controller no matter what the location.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class ArtefactTypeAstTransformation extends AbstractArtefactTypeAstTransformation {
    private static final ClassNode MY_TYPE = new ClassNode(Artefact.class);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();

    public void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: $node.class / $parent.class");
        }

        AnnotatedNode parent = (AnnotatedNode) astNodes[1];
        AnnotationNode node = (AnnotationNode) astNodes[0];
        if (!MY_TYPE.equals(node.getClassNode()) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode cNode = (ClassNode) parent;
        String cName = cNode.getName();
        if (cNode.isInterface()) {
            throw new RuntimeException("Error processing interface '" + cName + "'. " +
                    MY_TYPE_NAME + " not allowed for interfaces.");
        }

        Expression value = node.getMember("value");

        if (value != null && (value instanceof ConstantExpression)) {
            ConstantExpression ce = (ConstantExpression) value;
            String artefactType = ce.getText();
            performInjectionOnArtefactType(sourceUnit, cNode, artefactType);
        }
        else {
            throw new RuntimeException("Class ["+cName+"] contains an invalid @Artefact annotation. No artefact found for value specified.");
        }
    }

    public static void performInjectionOnArtefactType(SourceUnit sourceUnit, ClassNode cNode, String artefactType) {
        try {
            List<ClassInjector> injectors = ArtefactTypeAstTransformation.findInjectors(
                artefactType, GrailsAwareInjectionOperation.getClassInjectors());
            for (ClassInjector injector : injectors) {
                injector.performInjectionOnAnnotatedClass(sourceUnit, cNode);
            }
        } catch (RuntimeException e) {
            GrailsConsole.getInstance().error("Error occurred calling AST injector: " + e.getMessage(), e);
            throw e;
        }
    }


    public static List<ClassInjector> findInjectors(String artefactType, ClassInjector[] classInjectors) {
        List<ClassInjector> injectors = new ArrayList<ClassInjector>();
        for (ClassInjector classInjector : classInjectors) {
            if (classInjector instanceof AllArtefactClassInjector) {
                injectors.add(classInjector);
            }
            else if (classInjector instanceof GrailsArtefactClassInjector) {
                GrailsArtefactClassInjector gace = (GrailsArtefactClassInjector) classInjector;

                if (hasArtefactType(artefactType,gace)) {
                    injectors.add(gace);
                }
            }
        }
        return injectors;
    }

    public static boolean hasArtefactType(String artefactType, GrailsArtefactClassInjector gace) {
        for (String _artefactType : gace.getArtefactTypes()) {
            if (_artefactType.equals(artefactType)) {
                return true;
            }
        }
        return false;
    }
}

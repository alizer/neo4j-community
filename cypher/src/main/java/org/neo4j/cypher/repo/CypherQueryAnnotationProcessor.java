/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.repo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import org.neo4j.cypher.SyntaxException;
import org.neo4j.cypher.commands.Query;
import org.neo4j.cypher.javacompat.CypherParser;
import org.neo4j.helpers.Service;
import org.neo4j.kernel.impl.annotations.AnnotationProcessor;

@Service.Implementation( Processor.class )
@SupportedSourceVersion( SourceVersion.RELEASE_6 )
@SupportedAnnotationTypes( "org.neo4j.cypher.repo.CypherQuery" )
public class CypherQueryAnnotationProcessor extends AnnotationProcessor
{
    private final CypherParser parser = new CypherParser();

    @Override
    protected void process( TypeElement annotationType, Element annotated, AnnotationMirror mirror,
            Map<? extends ExecutableElement, ? extends AnnotationValue> values ) throws IOException
    {
        if ( !annotated.getModifiers().contains( Modifier.ABSTRACT ) )
        {
            processingEnv.getMessager().printMessage( Kind.ERROR, "CypherQuery methods must be abstract", annotated );
        }
        Query query = null;
        try
        {
            query = parser.parse( annotated.getAnnotation( CypherQuery.class ).value() );
        }
        catch ( SyntaxException e )
        {
            if ( mirror == null )
            {
                processingEnv.getMessager().printMessage( Kind.ERROR, e.getMessage(), annotated );
            }
            else
            {
                processingEnv.getMessager().printMessage( Kind.ERROR, e.getMessage(), annotated, mirror );
            }
        }
        Map<String, TypeMirror> parameters = new HashMap<String, TypeMirror>();
        for ( VariableElement param : ( (ExecutableElement) annotated ).getParameters() )
        {
            CypherQuery.Parameter annotation = param.getAnnotation( CypherQuery.Parameter.class );
            if ( annotation == null )
            {
                if ( !addAnnotation( param, CypherQuery.Parameter.class, param.getSimpleName().toString() ) )
                {
                    processingEnv.getMessager().printMessage( Kind.ERROR,
                            "Missing @CypherQuery.Parameter annotation for parameter '" + param.getSimpleName() + "'",
                            param );
                }
            }
            else
            {
                if ( null != parameters.put( annotation.value(), param.asType() ) )
                {
                    processingEnv.getMessager().printMessage( Kind.ERROR,
                            "Duplicate parameters: " + annotation.value(), param );
                }
            }
        }
        if ( query != null )
        {
            validateSignature( query, ( (ExecutableElement) annotated ).getReturnType(), parameters );
        }
    }

    private void validateSignature( Query query, TypeMirror returnType, Map<String, TypeMirror> parameters )
    {
        // TODO tobias Oct 4, 2011: Implement CypherQueryProcessor.validateSignature()
    }
}

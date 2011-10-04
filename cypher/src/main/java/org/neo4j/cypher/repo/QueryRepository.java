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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.neo4j.cypher.commands.Query;
import org.neo4j.cypher.javacompat.CypherParser;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

public class QueryRepository
{
    private static final Object[] NO_ARG = new Object[0];
    private final CypherParser parser;
    private final ExecutionEngine engine;

    public QueryRepository( GraphDatabaseService graphdb )
    {
        this.parser = new CypherParser();
        this.engine = new ExecutionEngine( graphdb );
    }

    public <Q> Q get( Class<Q> iface )
    {
        if ( !iface.isInterface() ) throw new IllegalArgumentException( "Not an interface: " + iface.getName() );
        Map<Method, QueryExecution> methods = new HashMap<Method, QueryRepository.QueryExecution>();
        for ( Method method : iface.getMethods() )
        {
            CypherQuery queryAnnnotation = method.getAnnotation( CypherQuery.class );
            if ( queryAnnnotation == null )
                throw new IllegalArgumentException( "Method " + method.getName() + " is not a CypherQuery." );
            Query query = parser.parse( queryAnnnotation.value() );
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            Type[] parameterTypes = method.getGenericParameterTypes();
            String[] parameters = new String[parameterAnnotations.length];
            for ( int i = 0; i < parameterAnnotations.length; i++ )
            {
                Annotation[] annotations = parameterAnnotations[i];
                for ( Annotation annotation : annotations )
                {
                    if ( annotation.annotationType() == CypherQuery.Parameter.class )
                    {
                        parameters[i] = ( (CypherQuery.Parameter) annotation ).value();
                        break;
                    }
                }
                if ( parameters[i] == null )
                    throw new IllegalArgumentException( "Missing parameter annotation (parameter " + ( i + 1 ) + ")" );
                validateParameter( query, parameters[i], parameterTypes[i] );
            }
            methods.put( method, new QueryExecution( query, resultConverter( query, method.getGenericReturnType() ),
                    parameters ) );
        }
        return iface.cast( proxy( iface, methods ) );
    }

    private ResultConverter resultConverter( Query query, Type type )
    {
        // FIXME: this is a crude implementation that does not consider the actual result type of the query
        for ( ResultConverter converter : ResultConverter.values() )
        {
            if ( converter.supports( type ) ) return converter;
        }
        throw new IllegalArgumentException( "Unsupported" );
    }

    private void validateParameter( Query query, String paramName, Type paramType )
    {
        // FIXME: should do validation here!
        // TODO: we should probably look up parameter converters similar to result converters
    }

    private Object proxy( Class<?> type, final Map<Method, QueryExecution> methods )
    {
        return Proxy.newProxyInstance( getClass().getClassLoader(), new Class<?>[] { type }, new InvocationHandler()
        {
            @Override
            public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable
            {
                return methods.get( method ).invoke( QueryRepository.this.engine, args == null ? NO_ARG : args );
            }
        } );
    }

    private static class QueryExecution
    {
        private final Query query;
        private final ResultConverter result;
        private final String[] parameters;

        QueryExecution( Query query, ResultConverter result, String[] parameters )
        {
            this.query = query;
            this.result = result;
            this.parameters = parameters;
        }

        public Object invoke( ExecutionEngine engine, Object[] args )
        {
            assert args.length == parameters.length : "not the expected number of arguments";
            Map<String, Object> params = new HashMap<String, Object>();
            for ( int i = 0; i < args.length; i++ )
            {
                params.put( parameters[i], args[i] );
            }
            return result.convert( engine.execute( query, params ) );
        }
    }

    private static enum ResultConverter
    {
        NODE
        {
            @Override
            boolean supports( Type type )
            {
                return type == Node.class;
            }

            @Override
            Object convert( ExecutionResult result )
            {
                return single( result.columnAs( singleColumn( result ) ) );
            }

        },
        NODE_ITERATOR
        {
            @Override
            boolean supports( Type type )
            {
                if ( type instanceof ParameterizedType )
                {
                    ParameterizedType param = (ParameterizedType) type;
                    if ( param.getRawType() != Iterator.class ) return false;
                    return param.getActualTypeArguments()[0] == Node.class;
                }
                return false;
            }

            @Override
            Object convert( ExecutionResult result )
            {
                return result.columnAs( singleColumn( result ) );
            }
        },
        NODE_ITERABLE
        {
            @Override
            boolean supports( Type type )
            {
                if ( type instanceof ParameterizedType )
                {
                    ParameterizedType param = (ParameterizedType) type;
                    if ( param.getRawType() != Iterable.class ) return false;
                    return param.getActualTypeArguments()[0] == Node.class;
                }
                return false;
            }

            @Override
            Object convert( final ExecutionResult result )
            {
                return new Iterable<Node>()
                {
                    @Override
                    public Iterator<Node> iterator()
                    {
                        return result.columnAs( singleColumn( result ) );
                    }
                };
            }
        };
        abstract boolean supports( Type type );

        abstract Object convert( ExecutionResult result );
    }

    private static String singleColumn( ExecutionResult result )
    {
        List<String> cols = result.columns();
        if ( cols.size() != 1 ) throw new RuntimeException( "too many columns returned from cypher: " + cols );
        return cols.get( 0 );
    }

    private static <T> T single( Iterator<T> iter )
    {
        if ( !iter.hasNext() ) return null;
        T value = iter.next();
        if ( iter.hasNext() ) throw new RuntimeException( "too many rows returned from cypher" );
        return value;
    }
}

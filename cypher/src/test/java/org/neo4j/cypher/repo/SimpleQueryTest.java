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

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.test.TargetDirectory;

import static org.junit.Assert.assertNotNull;

public class SimpleQueryTest
{
    interface Queries
    {
        @CypherQuery( "start n=node(0) return n" )
        Node getNodeZero();

        @CypherQuery( "start n=node({id}) return n" )
        Node getNodeById( @CypherQuery.Parameter( "id" ) long id );
    }

    private static final TargetDirectory target = TargetDirectory.forTest( SimpleQueryTest.class );
    private static GraphDatabaseService graphdb;
    private static QueryRepository repo;

    @BeforeClass
    public static void startGraphDB() throws Exception
    {
        graphdb = new EmbeddedGraphDatabase( target.graphDbDir( true ).getAbsolutePath() );
        repo = new QueryRepository( graphdb );
    }

    @AfterClass
    public static void stopGraphDB() throws Exception
    {
        if ( graphdb != null ) graphdb.shutdown();
        graphdb = null;
    }

    private Queries queries;

    @Before
    public void setup() throws Exception
    {
        queries = repo.get( Queries.class );
    }

    @Test
    public void canReturnSingleNode() throws Exception
    {
        assertNotNull( queries.getNodeZero() );
    }

    @Test
    public void canAcceptSingleParameter() throws Exception
    {
        assertNotNull( queries.getNodeById( 0 ) );
    }
}

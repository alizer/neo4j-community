/**
 * Copyright (c) 2002-2012 "Neo Technology,"
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
package org.neo4j.helpers;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ProgressTest
{
    @Test
    public void shouldReportProgressInTheSpecifiedIntervals() throws Exception
    {
        // given
        Progress.Indicator indicator = indicatorMock();
        Progress progress = factory.mock( indicator, 10 ).singlePart( testName.getMethodName(), 16 );

        // when
        progress.start();
        for ( int i = 0; i < 16; i++ )
        {
            progress.add( 1 );
        }
        progress.done();

        // then
        InOrder order = inOrder( indicator );
        order.verify( indicator ).startProcess( 16 );
        for ( int i = 0; i < 10; i++ )
        {
            order.verify( indicator ).progress( i, i + 1 );
        }
        order.verify( indicator ).completeProcess();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void shouldAggregateProgressFromMultipleProcesses() throws Exception
    {
        // given
        Progress.Indicator indicator = indicatorMock();
        Progress.MultiPartBuilder builder = factory.mock( indicator, 10 ).multipleParts( testName.getMethodName() );
        Progress first = builder.progressForPart( "first", 5 );
        Progress other = builder.progressForPart( "other", 5 );
        builder.complete();
        InOrder order = inOrder( indicator );
        order.verify( indicator ).startProcess( 10 );
        order.verifyNoMoreInteractions();

        // when
        first.start();
        for ( int i = 0; i < 5; i++ )
        {
            first.add( 1 );
        }
        first.done();

        // then
        order.verify( indicator ).startPart( "first", 5 );
        for ( int i = 0; i < 5; i++ )
        {
            order.verify( indicator ).progress( i, i + 1 );
        }
        order.verify( indicator ).completePart( "first" );
        order.verifyNoMoreInteractions();

        // when
        other.start();
        for ( int i = 0; i < 5; i++ )
        {
            other.add( 1 );
        }
        other.done();

        // then
        order.verify( indicator ).startPart( "other", 5 );
        for ( int i = 5; i < 10; i++ )
        {
            order.verify( indicator ).progress( i, i + 1 );
        }
        order.verify( indicator ).completePart( "other" );
        order.verify( indicator ).completeProcess();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void shouldNotAllowAddingPartsAfterCompletingMultiPartBuilder() throws Exception
    {
        // given
        Progress.MultiPartBuilder builder = factory.mock( indicatorMock(), 10 )
                                                   .multipleParts( testName.getMethodName() );
        builder.progressForPart( "first", 10 );
        builder.complete();

        // when
        try
        {
            builder.progressForPart( "other", 10 );
            fail( "should have thrown exception" );
        }
        // then
        catch ( IllegalStateException expected )
        {
            assertEquals( "Builder has been completed.", expected.getMessage() );
        }
    }

    @Test
    public void shouldNotAllowAddingMultiplePartsWithSameIdentifier() throws Exception
    {
        // given
        Progress.MultiPartBuilder builder = Mockito.mock( Progress.Factory.class )
                                                   .multipleParts( testName.getMethodName() );
        builder.progressForPart( "first", 10 );

        // when
        try
        {
            builder.progressForPart( "first", 10 );
            fail( "should have thrown exception" );
        }
        // then
        catch ( IllegalArgumentException expected )
        {
            assertEquals( "Part 'first' has already been defined.", expected.getMessage() );
        }
    }

    @Test
    public void shouldStartProcessAutomaticallyIfNotDoneBefore() throws Exception
    {
        // given
        Progress.Indicator indicator = indicatorMock();
        Progress progress = factory.mock( indicator, 10 ).singlePart( testName.getMethodName(), 16 );

        // when
        for ( int i = 0; i < 16; i++ )
        {
            progress.add( 1 );
        }
        progress.done();

        // then
        InOrder order = inOrder( indicator );
        order.verify( indicator, times( 1 ) ).startProcess( 16 );
        for ( int i = 0; i < 10; i++ )
        {
            order.verify( indicator ).progress( i, i + 1 );
        }
        order.verify( indicator ).completeProcess();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void shouldStartMultiPartProcessAutomaticallyIfNotDoneBefore() throws Exception
    {
        // given
        Progress.Indicator indicator = indicatorMock();
        Progress.MultiPartBuilder builder = factory.mock( indicator, 10 ).multipleParts( testName.getMethodName() );
        Progress first = builder.progressForPart( "first", 5 );
        Progress other = builder.progressForPart( "other", 5 );
        builder.complete();
        InOrder order = inOrder( indicator );
        order.verify( indicator ).startProcess( 10 );
        order.verifyNoMoreInteractions();

        // when
        for ( int i = 0; i < 5; i++ )
        {
            first.add( 1 );
        }
        first.done();

        // then
        order.verify( indicator ).startPart( "first", 5 );
        for ( int i = 0; i < 5; i++ )
        {
            order.verify( indicator ).progress( i, i + 1 );
        }
        order.verify( indicator ).completePart( "first" );
        order.verifyNoMoreInteractions();

        // when
        for ( int i = 0; i < 5; i++ )
        {
            other.add( 1 );
        }
        other.done();

        // then
        order.verify( indicator ).startPart( "other", 5 );
        for ( int i = 5; i < 10; i++ )
        {
            order.verify( indicator ).progress( i, i + 1 );
        }
        order.verify( indicator ).completePart( "other" );
        order.verify( indicator ).completeProcess();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void shouldCompleteMultiPartProgressWithNoPartsImmediately() throws Exception
    {
        // given
        Progress.Indicator indicator = indicatorMock();
        Progress.MultiPartBuilder builder = factory.mock( indicator, 10 ).multipleParts( testName.getMethodName() );

        // when
        builder.complete();

        // then
        InOrder order = inOrder( indicator );
        order.verify( indicator ).startProcess( 0 );
        order.verify( indicator ).progress( 0, 10 );
        order.verify( indicator ).completeProcess();
        order.verifyNoMoreInteractions();
    }

    private static Progress.Indicator indicatorMock()
    {
        Progress.Indicator indicator = mock( Progress.Indicator.class, Mockito.CALLS_REAL_METHODS );
        doNothing().when( indicator ).progress( anyInt(), anyInt() );
        return indicator;
    }

    private static final String EXPECTED_TEXTUAL_OUTPUT;

    static
    {
        StringWriter expectedTextualOutput = new StringWriter();
        for ( int i = 0; i < 10; )
        {
            for ( int j = 0; j < 20; j++ )
            {
                expectedTextualOutput.write( '.' );
            }
            expectedTextualOutput.write( String.format( " %3d%%%n", (++i) * 10 ) );
        }
        EXPECTED_TEXTUAL_OUTPUT = expectedTextualOutput.toString();
    }

    @Test
    public void shouldPrintADotEveryHalfPercentAndFullPercentageEveryTenPercentWithTextualIndicator() throws Exception
    {
        // given
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Progress progress = Progress.textual( stream ).singlePart( testName.getMethodName(), 1000 );

        // when
        for ( int i = 0; i < 1000; i++ )
        {
            progress.add( 1 );
        }

        // then
        assertEquals( testName.getMethodName() + "\n" + EXPECTED_TEXTUAL_OUTPUT,
                      stream.toString( Charset.defaultCharset().name() ) );
    }

    @Test
    public void shouldPrintADotEveryHalfPercentAndFullPercentageEveryTenPercentEvenWhenStepResolutionIsLower()
            throws Exception
    {
        // given
        StringWriter writer = new StringWriter();
        Progress progress = Progress.textual( writer ).singlePart( testName.getMethodName(), 50 );

        // when
        for ( int i = 0; i < 50; i++ )
        {
            progress.add( 1 );
        }

        // then
        assertEquals( testName.getMethodName() + "\n" + EXPECTED_TEXTUAL_OUTPUT,
                      writer.toString() );
    }

    @Test
    public void shouldPassThroughAllInvocationsOnDecorator() throws Exception
    {
        // given
        Progress.Indicator decorated = mock( Progress.Indicator.class );
        Progress.Indicator decorator = new Progress.Indicator.Decorator( decorated )
        {
        };

        // when
        decorator.startProcess( 4 );
        // then
        verify( decorated ).startProcess( 4 );

        // when
        decorator.startPart( "part1", 2 );
        // then
        verify( decorated ).startPart( "part1", 2 );

        // when
        decorator.progress( 0, 1 );
        // then
        verify( decorated ).progress( 0, 1 );

        // when
        decorator.startPart( "part2", 2 );
        // then
        verify( decorated ).startPart( "part2", 2 );

        // when
        decorator.progress( 1, 2 );
        // then
        verify( decorated ).progress( 1, 2 );

        // when
        decorator.completePart( "part1" );
        // then
        verify( decorated ).completePart( "part1" );

        // when
        decorator.progress( 2, 3 );
        // then
        verify( decorated ).progress( 2, 3 );

        // when
        decorator.completePart( "part2" );
        // then
        verify( decorated ).completePart( "part2" );

        // when
        decorator.progress( 3, 4 );
        // then
        verify( decorated ).progress( 3, 4 );

        // when
        decorator.completeProcess();
        // then
        verify( decorated ).completeProcess();
    }

    @Test
    public void shouldBeAbleToAwaitCompletionOfMultiPartProgress() throws Exception
    {
        // given
        Progress.MultiPartBuilder builder = Progress.Factory.NONE.multipleParts( testName.getMethodName() );
        Progress part1 = builder.progressForPart( "part1", 1 );
        Progress part2 = builder.progressForPart( "part2", 1 );
        final Progress.Completion completion = builder.complete();

        // when
        final CountDownLatch begin = new CountDownLatch( 1 ), end = new CountDownLatch( 1 );
        new Thread()
        {
            @Override
            public void run()
            {
                begin.countDown();
                try
                {
                    completion.await( 1, SECONDS );
                }
                catch ( Exception e )
                {
                    return; // do not count down the end latch
                }
                end.countDown();
            }
        }.start();
        Runnable callback = mock( Runnable.class );
        completion.notify( callback );
        assertTrue( begin.await( 1, SECONDS ) );

        // then
        verifyZeroInteractions( callback );

        // when
        try
        {
            completion.await( 1, TimeUnit.MILLISECONDS );
            fail( "should have thrown exception" );
        }
        // then
        catch ( TimeoutException expected )
        {
            assertEquals( "Process did not complete within 1 MILLISECONDS.", expected.getMessage() );
        }

        // when
        part1.done();
        // then
        verifyZeroInteractions( callback );

        // when
        part2.done();
        // then
        verify( callback ).run();
        completion.await( 0, TimeUnit.NANOSECONDS ); // should not have to wait
        assertTrue( end.await( 1, SECONDS ) ); // should have been completed

        // when
        callback = mock( Runnable.class );
        completion.notify( callback );
        verify( callback ).run();
    }

    @Test
    public void shouldNotAllowNullCompletionCallbacks() throws Exception
    {
        Progress.MultiPartBuilder builder = Progress.Factory.NONE.multipleParts( testName.getMethodName() );
        Progress.Completion completion = builder.complete();

        // when
        try
        {
            completion.notify( null );
            fail( "should have thrown exception" );
        }
        // then
        catch ( IllegalArgumentException expected )
        {
            assertEquals( "callback may not be null", expected.getMessage() );
        }
    }

    @Test
    public void shouldInvokeAllCallbacksEvenWhenOneThrowsException() throws Exception
    {
        // given
        Progress.MultiPartBuilder builder = Progress.Factory.NONE.multipleParts( testName.getMethodName() );
        Progress progress = builder.progressForPart( "only part", 1 );
        Progress.Completion completion = builder.complete();
        Runnable callback = mock( Runnable.class );
        doThrow( RuntimeException.class ).doNothing().when( callback ).run();
        completion.notify( callback );
        completion.notify( callback );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream sysErr = System.out;
        try
        {
            System.setErr( new PrintStream( out ) );

            // when
            progress.done();
        }
        finally
        {
            System.setOut( sysErr );
        }

        // then
        verify( callback, times( 2 ) ).run();
        String printedOutput = out.toString( Charset.defaultCharset().name() );
        assertTrue( printedOutput, printedOutput.startsWith( RuntimeException.class.getName() ) );
        assertTrue( printedOutput, printedOutput
                .contains( "\n\tat " + getClass().getName() + "." + testName.getMethodName() ) );
    }

    @Test
    public void shouldAllowStartingAPartBeforeCompletionOfMultiPartBuilder() throws Exception
    {
        // given
        Progress.Indicator indicator = mock( Progress.Indicator.class );
        Progress.MultiPartBuilder builder = factory.mock( indicator, 10 ).multipleParts( testName.getMethodName() );
        Progress part1 = builder.progressForPart( "part1", 1 );
        Progress part2 = builder.progressForPart( "part2", 1 );

        // when
        part1.add( 1 );
        builder.complete();
        part2.add( 1 );
        part1.done();
        part2.done();

        // then
        InOrder order = inOrder( indicator );
        order.verify( indicator ).startProcess( 2 );
        order.verify( indicator ).startPart( "part1", 1 );
        order.verify( indicator ).startPart( "part2", 1 );
        order.verify( indicator ).completePart( "part1" );
        order.verify( indicator ).completePart( "part2" );
        order.verify( indicator ).completeProcess();
    }

    @Rule
    public final TestName testName = new TestName();
    @Rule
    public final SingleIndicator factory = new SingleIndicator();

    private static class SingleIndicator implements TestRule
    {
        Progress.Factory mock( Progress.Indicator indicatorMock, int indicatorSteps )
        {
            when( indicatorMock.reportResolution() ).thenReturn( indicatorSteps );
            Progress.Factory factory = Mockito.mock( Progress.Factory.class );
            when( factory.newIndicator( any( String.class ) ) ).thenReturn( indicatorMock );
            factoryMocks.add( factory );
            return factory;
        }

        private final Collection<Progress.Factory> factoryMocks = new ArrayList<Progress.Factory>();

        @Override
        public Statement apply( final Statement base, Description description )
        {
            return new Statement()
            {
                @Override
                public void evaluate() throws Throwable
                {
                    base.evaluate();
                    for ( Progress.Factory factoryMock : factoryMocks )
                    {
                        verify( factoryMock, times( 1 ) ).newIndicator( any( String.class ) );
                    }
                }
            };
        }
    }
}

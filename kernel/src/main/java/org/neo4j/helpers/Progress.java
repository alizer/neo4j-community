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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

/**
 * A Progress object is an object through which a process can report its progress.
 * <p/>
 * Progress objects are not thread safe, and are to be used by a single thread only. Each Progress object from a {@link
 * Progress.MultiPartBuilder} can be used from different threads.
 *
 * @author Tobias Lindaaker <tobias.lindaaker@neotechnology.com>
 */
public abstract class Progress
{
    public static final Progress NONE = new Progress()
    {
        @Override
        public void start()
        {
            // do nothing
        }

        @Override
        public void set( long progress )
        {
            // do nothing
        }

        @Override
        public void add( long progress )
        {
            // do nothing
        }

        @Override
        public void done()
        {
            // do nothing
        }
    };

    public static Factory textual( final OutputStream out )
    {
        return textual( new PrintWriter( out ) );
    }

    public static Factory textual( final Writer out )
    {
        return new Factory()
        {
            @Override
            protected Indicator newIndicator( String process )
            {
                if ( out instanceof PrintWriter )
                {

                    return new Indicator.Textual( process, (PrintWriter) out );
                }
                else
                {
                    return new Indicator.Textual( process, new PrintWriter( out ) );
                }
            }
        };
    }

    public abstract void start();

    public abstract void set( long progress );

    public abstract void add( long progress );

    public abstract void done();

    public static abstract class Factory
    {
        public static final Factory NONE = new Factory()
        {
            @Override
            protected Indicator newIndicator( String process )
            {
                return Indicator.NONE;
            }
        };

        public final MultiPartBuilder multipleParts( String process )
        {
            return new MultiPartBuilder( this, process );
        }

        public final Progress singlePart( String process, long totalCount )
        {
            return new SinglePartProgress( newIndicator( process ), totalCount );
        }

        protected abstract Indicator newIndicator( String process );
    }

    public static final class MultiPartBuilder
    {
        private Aggregator aggregator = new Aggregator();
        private Set<String> parts = new HashSet<String>();
        private Completion completion = null;
        private final Factory factory;
        private final String process;

        private MultiPartBuilder( Factory factory, String process )
        {
            this.factory = factory;
            this.process = process;
        }

        public Progress progressForPart( String part, long totalCount )
        {
            if ( aggregator == null )
            {
                throw new IllegalStateException( "Builder has been completed." );
            }
            if ( !parts.add( part ) )
            {
                throw new IllegalArgumentException( String.format( "Part '%s' has already been defined.", part ) );
            }
            MultiPartProgress progress = new MultiPartProgress( aggregator, part, totalCount );
            aggregator.add( progress );
            return progress;
        }

        public Completion complete()
        {
            if ( aggregator != null )
            {
                completion = aggregator.initialize( factory.newIndicator( process ) );
            }
            aggregator = null;
            parts = null;
            return completion;
        }
    }

    public abstract static class Indicator
    {
        static final Indicator NONE = new Indicator( 1 )
        {
            @Override
            protected void progress( int from, int to )
            {
                // do nothing
            }
        };
        private final int reportResolution;

        public Indicator( int reportResolution )
        {
            this.reportResolution = reportResolution;
        }

        protected abstract void progress( int from, int to );

        int reportResolution()
        {
            return reportResolution;
        }

        public void startProcess( long totalCount )
        {
            // default: do nothing
        }

        public void startPart( String part, long totalCount )
        {
            // default: do nothing
        }

        public void completePart( String part )
        {
            // default: do nothing
        }

        public void completeProcess()
        {
            // default: do nothing
        }

        public static abstract class Decorator extends Indicator
        {
            private final Indicator indicator;

            public Decorator( Factory factory, String process )
            {
                this( factory.newIndicator( process ) );
            }

            public Decorator( Indicator indicator )
            {
                super( indicator.reportResolution() );
                this.indicator = indicator;
            }

            @Override
            public void startProcess( long totalCount )
            {
                indicator.startProcess( totalCount );
            }

            @Override
            public void startPart( String part, long totalCount )
            {
                indicator.startPart( part, totalCount );
            }

            @Override
            public void completePart( String part )
            {
                indicator.completePart( part );
            }

            @Override
            public void completeProcess()
            {
                indicator.completeProcess();
            }

            @Override
            protected void progress( int from, int to )
            {
                indicator.progress( from, to );
            }
        }

        static class Textual extends Indicator
        {
            private final String process;
            private final PrintWriter out;

            Textual( String process, PrintWriter out )
            {
                super( 200 );
                this.process = process;
                this.out = out;
            }

            @Override
            public void startProcess( long totalCount )
            {
                out.println( process );
            }

            @Override
            protected void progress( int from, int to )
            {
                for ( int i = from; i < to; )
                {
                    printProgress( ++i );
                }
                out.flush();
            }

            private void printProgress( int progress )
            {
                out.print( '.' );
                if ( progress % 20 == 0 )
                {
                    out.printf( " %3d%%%n", progress / 2 );
                }
            }
        }
    }

    public static final class Completion
    {
        private volatile Collection<Runnable> callbacks = new ArrayList<Runnable>();

        void complete()
        {
            Collection<Runnable> callbacks = this.callbacks;
            if ( callbacks != null )
            {
                Runnable[] targets;
                synchronized ( callbacks )
                {
                    targets = callbacks.toArray( new Runnable[callbacks.size()] );
                    this.callbacks = null;
                }
                for ( Runnable target : targets )
                {
                    try
                    {
                        target.run();
                    }
                    catch ( Exception e )
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void notify( Runnable callback )
        {
            if ( callback == null )
            {
                throw new IllegalArgumentException( "callback may not be null" );
            }
            Collection<Runnable> callbacks = this.callbacks;
            if ( callbacks != null )
            {
                synchronized ( callbacks )
                {
                    if ( this.callbacks == callbacks )
                    { // double checked locking
                        callbacks.add( callback );
                        callback = null; // we have not reached completion
                    }
                }
            }
            if ( callback != null )
            { // we have already reached completion
                callback.run();
            }
        }

        public void await( long timeout, TimeUnit unit ) throws InterruptedException, TimeoutException
        {
            CountDownLatch latch = null;
            Collection<Runnable> callbacks = this.callbacks;
            if ( callbacks != null )
            {
                synchronized ( callbacks )
                {
                    if ( this.callbacks == callbacks )
                    { // double checked locking
                        callbacks.add( new CountDown( latch = new CountDownLatch( 1 ) ) );
                    }
                }
            }
            if ( latch != null )
            { // await completion
                if ( !latch.await( timeout, unit ) )
                {
                    throw new TimeoutException(
                            String.format( "Process did not complete within %d %s.", timeout, unit.name() ) );
                }
            }
        }

        private static final class CountDown implements Runnable
        {
            private final CountDownLatch latch;

            CountDown( CountDownLatch latch )
            {
                this.latch = latch;
            }

            @Override
            public void run()
            {
                latch.countDown();
            }
        }
    }

    // --- IMPLEMENTATION ---

    private Progress()
    {
        // only internal implementations
    }

    private static final class SinglePartProgress extends Progress
    {
        private final Indicator indicator;
        private final long totalCount;
        private long value = 0;
        private int lastReported = 0;
        private boolean stared = false;

        SinglePartProgress( Indicator indicator, long totalCount )
        {
            this.indicator = indicator;
            this.totalCount = totalCount;
        }

        @Override
        public void start()
        {
            if ( !stared )
            {
                stared = true;
                indicator.startProcess( totalCount );
            }
        }

        @Override
        public void set( long progress )
        {
            update( value = progress );
        }

        @Override
        public void add( long progress )
        {
            update( value += progress );
        }

        @Override
        public void done()
        {
            set( totalCount );
            indicator.completeProcess();
        }

        private void update( long progress )
        {
            start();
            int current = (int) ((progress * indicator.reportResolution()) / totalCount);
            if ( current > lastReported )
            {
                indicator.progress( lastReported, current );
                lastReported = current;
            }
        }
    }

    private static final class MultiPartProgress extends Progress
    {
        private final Aggregator aggregator;
        private final String part;
        boolean started = false;
        private long value = 0, lastReported = 0;
        private final long totalCount;

        MultiPartProgress( Aggregator aggregator, String part, long totalCount )
        {
            this.aggregator = aggregator;
            this.part = part;
            this.totalCount = totalCount;
        }

        @Override
        public void start()
        {
            if ( !started )
            {
                aggregator.start( this );
                started = true;
            }
        }

        @Override
        public void set( long progress )
        {
            update( value = progress );
        }

        @Override
        public void add( long progress )
        {
            update( value += progress );
        }

        @Override
        public void done()
        {
            set( totalCount );
            aggregator.complete( this );
        }

        private void update( long progress )
        {
            start();
            if ( progress > lastReported )
            {
                aggregator.update( progress - lastReported );
                lastReported = progress;
            }
        }

        enum State
        {
            INIT, LIVE
        }
    }

    private static final class Aggregator
    {
        private final Map<MultiPartProgress, MultiPartProgress.State> states =
                new ConcurrentHashMap<MultiPartProgress, MultiPartProgress.State>();
        private Indicator indicator;
        private volatile long progress; // accessed through updater
        private volatile int last; // accessed through updater
        private static final AtomicLongFieldUpdater<Aggregator> PROGRESS = newUpdater( Aggregator.class, "progress" );
        private static final AtomicIntegerFieldUpdater<Aggregator> LAST =
                AtomicIntegerFieldUpdater.newUpdater( Aggregator.class, "last" );
        private long totalCount = 0;
        private final Completion completion = new Completion();

        synchronized void add( MultiPartProgress progress )
        {
            states.put( progress, MultiPartProgress.State.INIT );
            this.totalCount += progress.totalCount;
        }

        synchronized Completion initialize( Indicator indicator )
        {
            this.indicator = indicator;
            indicator.startProcess( totalCount );
            if ( states.isEmpty() )
            {
                indicator.progress( 0, indicator.reportResolution() );
                indicator.completeProcess();
            }
            return completion;
        }

        void update( long delta )
        {
            long progress = PROGRESS.addAndGet( this, delta );
            int current = (int) ((progress * indicator.reportResolution()) / totalCount);
            for ( int last = this.last; current > last; last = this.last )
            {
                if ( LAST.compareAndSet( this, last, current ) )
                {
                    synchronized ( this )
                    {
                        indicator.progress( last, current );
                    }
                }
            }
        }

        void start( MultiPartProgress part )
        {
            if ( states.put( part, MultiPartProgress.State.LIVE ) == MultiPartProgress.State.INIT )
            {
                indicator.startPart( part.part, part.totalCount );
            }
        }

        void complete( MultiPartProgress part )
        {
            if ( states.remove( part ) != null )
            {
                indicator.completePart( part.part );
                if ( states.isEmpty() )
                {
                    indicator.completeProcess();
                    completion.complete();
                }
            }
        }
    }
}

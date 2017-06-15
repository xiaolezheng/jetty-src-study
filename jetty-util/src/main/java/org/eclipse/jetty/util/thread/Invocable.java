//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util.thread;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * <p>A task (typically either a {@link Runnable} or {@link Callable}
 * that declares how it will behave when invoked:</p>
 * <ul>
 * <li>blocking, the invocation will certainly block (e.g. performs blocking I/O)</li>
 * <li>non-blocking, the invocation will certainly <strong>not</strong> block</li>
 * <li>either, the invocation <em>may</em> block</li>
 * </ul>
 * 
 * <p>
 * Static methods and are provided that allow the current thread to be tagged 
 * with a {@link ThreadLocal} to indicate if it has a blocking invocation type.
 * </p>
 * 
 */
public interface Invocable
{
    enum InvocationType
    {
        BLOCKING, NON_BLOCKING, EITHER
    }

    static ThreadLocal<Boolean> __nonBlocking = new ThreadLocal<Boolean>()
    {
        @Override
        protected Boolean initialValue()
        {
            return Boolean.FALSE;
        }
    };

    /**
     * Test if the current thread has been tagged as non blocking
     * @return True if the task the current thread is running has
     * indicated that it will not block.
     */
    public static boolean isNonBlockingInvocation()
    {
        return __nonBlocking.get();
    }

    /**
     * Invoke a task with the calling thread, tagged to indicate
     * that it will not block.
     * @param task The task to invoke.
     */
    public static void invokeNonBlocking(Runnable task)
    {
        // a Choice exists, so we must indicate NonBlocking
        Boolean was_non_blocking = __nonBlocking.get();
        try
        {
            __nonBlocking.set(Boolean.TRUE);
            task.run();
        }
        finally
        {
            __nonBlocking.set(was_non_blocking);
        }
    }

    /**
     * Invoke a task with the calling thread.
     * If the task is an {@link Invocable} of {@link InvocationType#EITHER}
     * then it is invoked with {@link #invokeNonBlocking(Runnable)}, to 
     * indicate the type of invocation that has been assumed.
     * @param task The task to invoke.
     */
    public static void invokePreferNonBlocking(Runnable task)
    {
        switch (getInvocationType(task))
        {
            case BLOCKING:
            case NON_BLOCKING:
                task.run();
                break;

            case EITHER:
                // a Choice exists, so we must indicate NonBlocking
                invokeNonBlocking(task);
                break;
        }
    }

    /**
     * Invoke a task with the calling thread.
     * If the task is an {@link Invocable} of {@link InvocationType#EITHER}
     * and the preferredInvocationType is not {@link InvocationType#BLOCKING}
     * then it is invoked with {@link #invokeNonBlocking(Runnable)}.
     * @param task The task to invoke.
     * @param preferredInvocationType The invocation type to use if the task
     * does not indicate a preference.
     */
    public static void invokePreferred(Runnable task, InvocationType preferredInvocationType)
    {
        switch (getInvocationType(task))
        {
            case BLOCKING:
            case NON_BLOCKING:
                task.run();
                break;

            case EITHER:
                if (getInvocationType(task) == InvocationType.EITHER && preferredInvocationType == InvocationType.NON_BLOCKING)
                    invokeNonBlocking(task);
                else
                    task.run();
                break;
        }
    }

    /**
     * wrap a task with the to indicate invocation type.
     * If the task is an {@link Invocable} of {@link InvocationType#EITHER}
     * and the preferredInvocationType is not {@link InvocationType#BLOCKING}
     * then it is wrapped with an invocation of {@link #invokeNonBlocking(Runnable)}.
     * otherwise the task itself is returned.
     * @param task The task to invoke.
     * @param preferredInvocationType The invocation type to use if the task
     * does not indicate a preference.
     * @return A Runnable that invokes the task in the declared or preferred type.
     */
    public static Runnable asPreferred(Runnable task, InvocationType preferredInvocationType)
    {
        switch (getInvocationType(task))
        {
            case BLOCKING:
            case NON_BLOCKING:
                break;

            case EITHER:
                if (preferredInvocationType == InvocationType.NON_BLOCKING)
                    return () -> invokeNonBlocking(task);
                break;
        }

        return task;
    }

    /**
     * Get the invocation type of an Object.
     * @param o The object to check the invocation type of.
     * @return If the object is a {@link Invocable}, it is coerced and the {@link #getInvocationType()}
     * used, otherwise {@link InvocationType#BLOCKING} is returned.
     */
    public static InvocationType getInvocationType(Object o)
    {
        if (o instanceof Invocable)
            return ((Invocable)o).getInvocationType();
        return InvocationType.BLOCKING;
    }

    /**
     * @return The InvocationType of this object
     */
    default InvocationType getInvocationType()
    {
        return InvocationType.BLOCKING;
    }
    
    /**
     * An Executor wrapper that knows about Invocable
     *
     */
    public static class InvocableExecutor implements Executor
    {
        private static final Logger LOG = Log.getLogger(InvocableExecutor.class);

        private final Executor _executor;
        private final InvocationType _preferredInvocationForExecute;
        private final InvocationType _preferredInvocationForInvoke;

        public InvocableExecutor(Executor executor,InvocationType preferred)
        {
            this(executor,preferred,preferred);
        }
        
        public InvocableExecutor(Executor executor,InvocationType preferredInvocationForExecute,InvocationType preferredInvocationForIvoke)
        {
            _executor=executor;
            _preferredInvocationForExecute=preferredInvocationForExecute;
            _preferredInvocationForInvoke=preferredInvocationForIvoke;
        }

        public Invocable.InvocationType getPreferredInvocationType()
        {
            return _preferredInvocationForInvoke;
        }

        public void invoke(Runnable task)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("{} invoke  {}", this, task);
            Invocable.invokePreferred(task,_preferredInvocationForInvoke);
            if (LOG.isDebugEnabled())
                LOG.debug("{} invoked {}", this, task);
        }
        
        public void execute(Runnable task)
        {
            tryExecute(task,_preferredInvocationForExecute);
        }

        public void execute(Runnable task, InvocationType preferred)
        {
            tryExecute(task,preferred);
        }
        
        public boolean tryExecute(Runnable task)
        {
            return tryExecute(task,_preferredInvocationForExecute);
        }
        
        public boolean tryExecute(Runnable task, InvocationType preferred)
        {
            try
            {
                _executor.execute(Invocable.asPreferred(task,preferred));
                return true;
            }
            catch(RejectedExecutionException e)
            {
                // If we cannot execute, then close the task
                LOG.debug(e);
                LOG.warn("Rejected execution of {}",task);
                try
                {
                    if (task instanceof Closeable)
                        ((Closeable)task).close();
                }
                catch (Exception x)
                {
                    e.addSuppressed(x);
                    LOG.warn(e);
                }
            }
            return false;
        }

    }
}

/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.core.client.impl;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.core.client.HornetQClientLogger;
import org.hornetq.core.client.HornetQClientMessageBundle;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A ClientProducerCreditsImpl
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
public class ClientProducerCreditsImpl implements ClientProducerCredits
{
   private final Semaphore semaphore;

   private final int windowSize;

   private volatile boolean closed;

   private boolean blocked;

   private final SimpleString address;

   private final ClientSessionInternal session;

   private int pendingCredits;

   private int arriving;

   private int refCount;

   private boolean serverRespondedWithFail;

   public ClientProducerCreditsImpl(final ClientSessionInternal session,
                                    final SimpleString address,
                                    final int windowSize)
   {
      this.session = session;

      this.address = address;

      this.windowSize = windowSize / 2;

      // Doesn't need to be fair since session is single threaded

      semaphore = new Semaphore(0, false);
   }

   public void init()
   {
      // We initial request twice as many credits as we request in subsequent requests
      // This allows the producer to keep sending as more arrive, minimising pauses
      checkCredits(windowSize);
   }

   public void acquireCredits(final int credits) throws InterruptedException, HornetQException
   {
      checkCredits(credits);


      boolean tryAcquire;

      synchronized (this)
      {
         tryAcquire = semaphore.tryAcquire(credits);
      }

      if (!tryAcquire)
      {
         if (!closed)
         {
            this.blocked = true;
            try
            {
               while (!semaphore.tryAcquire(credits, 10, TimeUnit.SECONDS))
               {
                  // I'm using string concatenation here in case address is null
                  // better getting a "null" string than a NPE
                  HornetQClientLogger.LOGGER.outOfCreditOnFlowControl("" + address);
               }
            }
            finally
            {
               this.blocked = false;
            }
         }
      }


      synchronized (this)
      {
         pendingCredits -= credits;
      }

      // check to see if the blocking mode is FAIL on the server
      synchronized (this)
      {
         if (serverRespondedWithFail)
         {
            serverRespondedWithFail = false;

            // remove existing credits to force the client to ask the server for more on the next send
            semaphore.drainPermits();
            pendingCredits = 0;
            arriving = 0;

            throw HornetQClientMessageBundle.BUNDLE.addressIsFull(address.toString(), credits);
         }
      }
   }

   public boolean isBlocked()
   {
      return blocked;
   }

   public int getBalance()
   {
      return semaphore.availablePermits();
   }

   public void receiveCredits(final int credits)
   {
      synchronized (this)
      {
         arriving -= credits;
      }

      semaphore.release(credits);
   }

   public void receiveFailCredits(final int credits)
   {
      serverRespondedWithFail = true;
      // receive credits like normal to keep the sender from blocking
      receiveCredits(credits);
   }

   public synchronized void reset()
   {
      // Any pendingCredits credits from before failover won't arrive, so we re-initialise

      semaphore.drainPermits();

      int beforeFailure = pendingCredits;

      pendingCredits = 0;
      arriving = 0;

      // If we are waiting for more credits than what's configured, then we need to use what we tried before
      // otherwise the client may starve as the credit will never arrive
      checkCredits(Math.max(windowSize * 2, beforeFailure));
   }

   public void close()
   {
      // Closing a producer that is blocking should make it return
      closed = true;

      semaphore.release(Integer.MAX_VALUE / 2);
   }

   public synchronized void incrementRefCount()
   {
      refCount++;
   }

   public synchronized int decrementRefCount()
   {
      return --refCount;
   }

   public synchronized void releaseOutstanding()
   {
      semaphore.drainPermits();
   }

   private void checkCredits(final int credits)
   {
      int needed = Math.max(credits, windowSize);

      int toRequest = -1;

      synchronized (this)
      {
         if (semaphore.availablePermits() + arriving < needed)
         {
            toRequest = needed - arriving;

            pendingCredits += toRequest;
            arriving += toRequest;
         }
      }

      if (toRequest != -1)
      {
         requestCredits(toRequest);
      }
   }

   private void requestCredits(final int credits)
   {
      session.sendProducerCreditsMessage(credits, address);
   }
}
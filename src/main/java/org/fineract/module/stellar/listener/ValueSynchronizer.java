/**
 * Copyright 2016 Myrle Krantz
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
package org.fineract.module.stellar.listener;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ValueSynchronizer<T> {
  private final LoadingCache<T, Lock> valueLocks;

  public ValueSynchronizer() {
    valueLocks = CacheBuilder.newBuilder().build(
      new CacheLoader<T, Lock>() {
        public Lock load(final T id) {
          return new ReentrantLock();
        }
      });
  }

  public void sync(final T onValue, final Runnable toDo)
  {
    final Lock lock = valueLocks.getUnchecked(onValue);
    lock.lock();

    try {
      toDo.run();
    }
    finally {
      lock.unlock();
    }
  }
}

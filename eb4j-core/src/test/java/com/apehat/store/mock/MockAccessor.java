/*
 * Copyright Apehat.com
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

package com.apehat.store.mock;

import com.apehat.store.Commander;
import com.apehat.store.Accessor;
import com.apehat.store.Querier;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author hanpengfei
 * @since 1.0
 */
public class MockAccessor implements Accessor {

    private final ReentrantLock connectLock = new ReentrantLock();

    private final Commander commander;
    private final Querier querier;

    public MockAccessor(Commander commander, Querier querier) {
        this.commander = Objects.requireNonNull(commander);
        this.querier = Objects.requireNonNull(querier);
    }

    @Override
    public void access() throws Exception {
        connectLock.lockInterruptibly();
    }

    @Override
    public Querier getQuerier() {
        return querier;
    }

    @Override
    public Commander getCommander() {
        return commander;
    }

    @Override
    public boolean isOpened() {
        return connectLock.isLocked();
    }

    @Override
    public void close() throws Exception {
        connectLock.unlock();
    }
}

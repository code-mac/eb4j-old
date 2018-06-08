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
import com.apehat.store.Store;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;

/**
 * @author hanpengfei
 * @since 1.0
 */
public class MockStore implements Store {

    private final Accessor accessor;
    private final Collection<Object> collation;
    private final String accessToken;

    public MockStore(String accessToken) {
        this.accessToken = Objects.requireNonNull(accessToken);
        this.collation = new LinkedList<>();
        Commander commander = new MockCommander();
        Querier querier = new MockQuerier();
        this.accessor = new MockAccessor(commander, querier);
    }

    @Override
    public synchronized Accessor getAccessor(String accessToken) throws IllegalAccessException {
        if (!Objects.equals(this.accessToken, accessToken)) {
            throw new IllegalAccessException();
        }
        return accessor;
    }

    class MockCommander implements Commander {

        @Override
        public void add(Object o) {
            collation.add(o);
        }
    }

    class MockQuerier implements Querier {
        @Override
        public int size() {
            return collation.size();
        }
    }
}

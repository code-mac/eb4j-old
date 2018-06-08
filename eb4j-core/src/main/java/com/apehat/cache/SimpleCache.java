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

package com.apehat.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author hanpengfei
 * @since 1.0
 */
public class SimpleCache<T> implements Cache<T> {

    private final Set<T> values = new CopyOnWriteArraySet<>();

    @Override
    public void cache(T value) {
        values.add(value);
    }

    @Override
    public void remove(T value) {
        values.remove(value);
    }

    @Override
    public Collection<T> values() {
        return Collections.unmodifiableSet(values);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean contains(T value) {
        synchronized (values) {
            return values.contains(value);
        }
    }

    @Override
    public void clear() {
        values.clear();
    }
}

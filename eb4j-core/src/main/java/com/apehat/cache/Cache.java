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

/**
 * 缓存是同步的
 *
 * @author hanpengfei
 * @since 1.0
 */
public interface Cache<T> {

    void cache(T value);

    void remove(T value);

    Collection<T> values();

    int size();

    boolean contains(T value);

    void clear();

    // 刷新当前数据到给定的库
    default void flush() {

    }
}

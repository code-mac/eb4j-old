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

package com.apehat.eb4j;

import com.apehat.eb4j.event.PendingEvents;
import com.apehat.eb4j.event.PendingEventsFactory;
import com.apehat.eb4j.subscription.SubscriberStore;
import com.apehat.eb4j.subscription.SubscriberStoreFactory;

/**
 * @author hanpengfei
 * @since 1.0
 */

public class SubscriberEventQueueFactory implements SubscriberStoreFactory, PendingEventsFactory {
    private MemSubmitStore submitStore;

    @Override
    public PendingEvents newQueue() {
        return getInstance();
    }

    @Override
    public SubscriberStore newStore() {
        return getInstance();
    }

    private MemSubmitStore getInstance() {
        if (submitStore == null) {
            init();
        }
        return submitStore;
    }

    private synchronized void init() {
        if (submitStore == null) {
            submitStore = new MemSubmitStore();
        }
    }
}

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

import com.apehat.cache.Cache;
import com.apehat.cache.SimpleCache;
import com.apehat.eb4j.event.Event;
import com.apehat.eb4j.event.PendingEvents;
import com.apehat.eb4j.event.PendingEventsFactory;
import com.apehat.eb4j.poster.DefaultPosterFactory;
import com.apehat.eb4j.poster.Poster;
import com.apehat.eb4j.poster.PosterFactory;
import com.apehat.eb4j.subscription.*;
import com.apehat.lang.annotation.Completed;
import com.apehat.store.Accessor;
import com.apehat.store.CacheFactory;
import com.apehat.store.Store;
import com.apehat.store.StoreFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 建立事件的防止多次发布，可以通过事件的Tag进行记录
 *
 * @author hanpengfei
 * @since 1.0
 */
public final class EventBus {

    /**
     * Submit lock, only be used by {@link #submit(Event)}}
     */
    private final ReentrantLock submitLock = new ReentrantLock();

    /** Register lock, only be used by {@link #register(Subscriber)} */
    private final ReentrantLock registerLock = new ReentrantLock();

    /** Post lock, only be used by {@link #publish(Event)} */
    private final ReentrantLock postLock = new ReentrantLock();

    /**
     * The subscriber filter, be used to filter subscribers.
     * <p>
     * May can open this filed to user;
     * Mat can use filter chain, too
     */
    private final SubscriberFilter subscriberFilter = new EventTypeFilter();

    private final Poster syncPoster;
    private final Poster asyncPoster;

    /**
     * By open PendingEvents and SubscriberStore, user can select suitable store policy.
     * Of course, should provide some default implementor.
     * <p>
     * For example:
     * 1. Memory Store
     * 2. File System Store
     * 3. Database Store -- only need DataSource
     */
    private final PendingEvents pendingEvents;

    private final SubscriberStore subscriberStore;

    /**
     * Event cache. This will be used to cache submitted event when {@code pendingEvents} be
     * locked.
     */
    private final Cache<Event> eventCache = new SimpleCache<>();
    /**
     * Subscriber cache. This will be used to cache registered subscriber when {@code
     * subscriberStore} be locked.
     */
    private final Cache<Subscriber> subscriberCache = new SimpleCache<>();
    private final ReentrantLock publishLock = new ReentrantLock();

    /** The subscriber store accessor, be used to access subscriber store */
    private final Accessor<Subscriber> subscriberStoreAccessor;

    private Accessor<Event> eventCacheAccessor;

    private Accessor<Subscriber> subscriberCacheAccessor;

    private EventBus(EventBus.Builder builder) {
        this.syncPoster = builder.posterFactory.syncPoster();
        this.asyncPoster = builder.posterFactory.asyncPoster();

        assert !Objects.equals(syncPoster, asyncPoster);

        this.pendingEvents = builder.pendingEventsFactory.newQueue();
        this.subscriberStore = builder.subscriberStoreFactory.newStore();
        assert subscriberStore.size() == 0;

        final String accessToken = UUID.randomUUID().toString();

        try {
            Store<Subscriber> subscriberStore = builder.sf.newStore(accessToken);
            this.subscriberStoreAccessor = subscriberStore.getAccessor(accessToken);

            com.apehat.store.Cache<Event> eventCache = builder.eventCacheFactory
                    .newCache(accessToken);
            this.eventCacheAccessor = eventCache.getAccessor(accessToken);

            com.apehat.store.Cache<Subscriber> subscriberCache = builder.subscriberCacheFactory
                    .newCache(accessToken);
            this.subscriberCacheAccessor = subscriberCache.getAccessor(accessToken);
        } catch (IllegalAccessException e) {
            throw new RuntimeException();
        }
        // 启动新线程刷新
        // 启动新线程发布

        // 异步启动一个线程去启动 flush() 等待
    }

    /**
     * Async publish a event. If current thread is publishing event, this action will return.
     * <p>
     * 异步发布一个事件，当前线程正在有事件
     * 一般情况下, 事件总是被同步发布
     * 订阅者可能会进行异步执行
     * <p>
     * Post model:
     * 1. Same thread: the event only be posted at the caller thread
     * 2. Main thread: the event only be posted at the main thread
     * 3. Background thread: the event submit at background thread
     */
    public void submit(Event event) throws InterruptedException {
        if (event == null) {
            throw new NullPointerException();
        }
        final ReentrantLock eventSubmitLock = this.submitLock;
        eventSubmitLock.lockInterruptibly();
        try {
            // todo 此时应该锁缓存，防止缓存正在被刷新清除而导致并发修改异常
            eventCache.cache(event);
        } finally {
            eventSubmitLock.lockInterruptibly();
        }
        // 当publish未被调用时，需要显示启动
        // 可根据事件总线的状态进行检查（参考线程状态）
        // 这样可以避免 flush（）方法长期处于一个阻塞线程中
        // 降低系统资源的消耗
    }

    /**
     * Sync publish a event. If the sync poster is posting event, the current thread will blocking.
     *
     * @param event the event to publish
     * @throws InterruptedException the current be interrupted
     */
    @Completed
    public void publish(Event event) throws Exception {
        if (event == null) {
            throw new NullPointerException();
        }

        // get subscribers from cache
        // the first time to get form cache
        // can ensure needn't to filter with timestamp
        final Collection<Subscriber> cachedSubscribers;
        // 修改为通过访问器的访问模式
        //        synchronized (subscriberCache) {
        //            cachedSubscribers = subscriberCache.values();
        //        }
        subscriberCacheAccessor.access();
        cachedSubscribers = subscriberCacheAccessor.getQuerier().values();

        // the cachedSubscribers must be set to unmodifiable
        // prevent filter modify it
        final Collection<Subscriber> unmodifiableCachedSubscribers = Collections
                .unmodifiableCollection(cachedSubscribers);

        final Set<Subscriber> subscribers = new LinkedHashSet<>(
                subscriberFilter.doFilter(event, unmodifiableCachedSubscribers));

        // get subscribers of subscriberStore
        // must ensure subscriberStore isn't be modifying
        final Collection<Subscriber> storedSubscribers = subscriberStore.subscribersOf(event);

        subscribers.addAll(storedSubscribers);

        final ReentrantLock postLock = this.postLock;
        postLock.lockInterruptibly();
        try {
            // use two poster to post event
            // ensure the state of poster not destroyed
            syncPoster.post(event, subscribers);
        } finally {
            postLock.unlock();
        }
    }

    private void flush() {
        // 保存当前队列已经为空
        assert !pendingEvents.hasNext();
        // 将缓冲中的事件与订阅者刷新到仓储中
        // 需要加锁
        // 需要信号的等待
        // 与publish是协作工作的

        // 每次刷新完成后调用发布
    }

    // 异步进行事件的发送
    private void publish() throws InterruptedException {
        // 此处需要锁定事件队列与订阅者仓储，
        // 必须为可重入锁，防止事件队列与订阅者仓储是同一个实现
        final ReentrantLock publishLock = this.publishLock;

        publishLock.lockInterruptibly();
        try {
            // 当每一次发布循环完成后，将进行信号的传输
            while (pendingEvents.hasNext()) {
                Event event;
                Collection<Subscriber> subscribers;
                synchronized (pendingEvents) {
                    synchronized (subscriberStore) {
                        event = pendingEvents.nextEvent();
                        subscribers = subscriberStore.subscribersOf(event);
                    }
                }
                asyncPoster.post(event, subscribers);
            }
        } finally {
            publishLock.unlock();
        }
        // 发送信号
        // 也可以进行flush的调用
    }

    /**
     * 该方法是一个异步方法，所以在确保订阅者可以被用于订阅后，该方法将立即返回
     * <p>
     * Register a subscriber. The subscriber will be used to subscribe events with
     * {@code subscriber.subscribeTo()}.
     * <p>
     * Subscribe model:
     * 1. Subscribe events of current thread
     * 2. Subscribe global events
     * 3. Subscribe bus global event
     *
     * @throws RegisterException specified subscriber already be registered to this
     */
    public void register(Subscriber subscriber) throws InterruptedException {
        if (subscriber == null) {
            throw new NullPointerException();
        }
        if (isRegisteredSubscriber(subscriber)) {
            throw new RegisterException(subscriber, " already registered.");
        }

        final ReentrantLock subscriberRegisterLock = this.registerLock;
        subscriberRegisterLock.lockInterruptibly();
        try {
            subscriberCache.cache(subscriber);
        } finally {
            subscriberRegisterLock.unlock();
        }
    }

    private boolean isRegisteredSubscriber(Subscriber subscriber) {
        // 进行仓储与缓存的检查
        synchronized (subscriberCache) {
            if (subscriberCache.contains(subscriber)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unregister specified subscriber.
     *
     * @param subscriber the subscriber to unregister
     */
    public void unregister(Subscriber subscriber) {
        if (subscriber == null) {
            throw new NullPointerException();
        }
        // 取消注册，可以使用标记，而不必先修改订阅者仓储
        // 这样可以使得取消订阅不必修改仓储
        // 可以选择合适的时机进行订阅仓储的修改
        // 此时可以通过创建一个订阅者包装器来实现
    }

    public final static class Builder {

        private PosterFactory posterFactory;
        private PendingEventsFactory pendingEventsFactory;
        private SubscriberStoreFactory subscriberStoreFactory;

        private StoreFactory<Subscriber> sf;
        private CacheFactory<Event> eventCacheFactory;
        private CacheFactory<Subscriber> subscriberCacheFactory;

        public Builder setPublisherBuilder(PosterFactory posterFactory) {
            this.posterFactory = Objects.requireNonNull(posterFactory);
            return this;
        }

        public Builder setSubscriberStoreFactory(SubscriberStoreFactory subscriberStoreFactory) {
            this.subscriberStoreFactory = Objects.requireNonNull(subscriberStoreFactory);
            return this;
        }

        public Builder setEventQueue(PendingEventsFactory pendingEventsFactory) {
            this.pendingEventsFactory = Objects.requireNonNull(pendingEventsFactory);
            return this;
        }

        public EventBus build() {
            if (posterFactory == null) {
                this.posterFactory = new DefaultPosterFactory();
            }
            if (pendingEventsFactory == null) {
                pendingEventsFactory = new SubscriberEventQueueFactory();
            }
            if (subscriberStoreFactory == null) {
                subscriberStoreFactory = pendingEventsFactory instanceof SubscriberStoreFactory
                                         ? (SubscriberStoreFactory) pendingEventsFactory
                                         : new SubscriberEventQueueFactory();
            }
            return new EventBus(this);
        }
    }
}

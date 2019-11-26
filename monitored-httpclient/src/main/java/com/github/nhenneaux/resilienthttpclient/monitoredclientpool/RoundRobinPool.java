package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * A round-robin accessor for a list with a health check for each item.
 */
class RoundRobinPool {
    static final RoundRobinPool EMPTY = new RoundRobinPool(Collections.emptyList());

    private final List<SingleIpHttpClient> list;
    private final AtomicInteger position = new AtomicInteger(-1);


    /**
     * Create a new instance with a list that has constant time access as defined in {@link RandomAccess}
     *
     * @param list a list of service instance.
     */
    RoundRobinPool(final List<SingleIpHttpClient> list) {
        final List<SingleIpHttpClient> copiedList = new ArrayList<>(list);
        Collections.shuffle(copiedList);
        this.list = Collections.unmodifiableList(copiedList);
    }


    /**
     * @return an empty optional if the list is empty, the next element of the list (if the last index is reached, the first element is returned) otherwise
     */
    Optional<SingleIpHttpClient> next() {
        final Optional<List<SingleIpHttpClient>> activeList = Optional.of(
                list.stream()
                        .filter(SingleIpHttpClient::isHealthy)
                        .collect(Collectors.toUnmodifiableList())
        );

        return activeList
                .filter(not(List::isEmpty))
                .map(healthyItems -> healthyItems.get(position.updateAndGet(v -> (v + 1) % healthyItems.size())));
    }

    List<SingleIpHttpClient> getList() {
        return list;
    }

    @Override
    public String toString() {
        return "GenericRoundRobinListWithHealthCheck{" +
                "list=" + list +
                ", position=" + position +
                '}';
    }
}
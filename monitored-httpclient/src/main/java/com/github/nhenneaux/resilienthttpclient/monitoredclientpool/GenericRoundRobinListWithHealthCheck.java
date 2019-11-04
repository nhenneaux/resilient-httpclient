package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * A round-robin accessor for a list with a health check for each item.
 */
class GenericRoundRobinListWithHealthCheck<T extends ItemWithHealth> extends GenericRoundRobinList<T> {

    /**
     * Create a new instance with a list that has constant time access as defined in {@link RandomAccess}
     *
     * @param list a list of service instance.
     */
    GenericRoundRobinListWithHealthCheck(List<T> list) {
        super(list);
    }

    /**
     * @return an empty optional if the list is empty, the next element of the list (if the last index is reached, the first element is returned) otherwise
     */
    @Override
    public Optional<T> next() {
        final Optional<List<T>> activeList = Optional.of(
                getList().stream()
                        .filter(ItemWithHealth::isHealthy)
                        .collect(Collectors.toUnmodifiableList())
        );

        return activeList
                .filter(not(List::isEmpty))
                .map(healthyItems -> healthyItems.get(getPosition().updateAndGet(v -> (v + 1) % healthyItems.size())));
    }


}
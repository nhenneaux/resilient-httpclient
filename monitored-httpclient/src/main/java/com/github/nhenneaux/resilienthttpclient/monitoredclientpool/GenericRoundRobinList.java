package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.function.Predicate.not;

/**
 * A round-robin accessor for a list.
 */
class GenericRoundRobinList<T> implements RoundRobinList<T> {


    private final List<T> list;
    private final AtomicInteger position = new AtomicInteger(-1);


    /**
     * Create a new instance with a list that has constant time access as defined in {@link RandomAccess}
     *
     * @param list a list of service instance.
     */
    GenericRoundRobinList(final List<T> list) {
        final List<T> copiedList = new ArrayList<>(list);
        Collections.shuffle(copiedList);
        this.list = Collections.unmodifiableList(copiedList);
    }

    /**
     * @return an empty optional if the list is empty, the next element of the list (if the last index is reached, the first element is returned) otherwise
     */
    @Override
    public Optional<T> next() {
        return Optional.of(list)
                .filter(not(List::isEmpty))
                .map(healthyItems -> healthyItems.get(position.updateAndGet(v -> (v + 1) % healthyItems.size())));
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public List<T> getList() {
        return list;
    }

    AtomicInteger getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return "GenericRoundRobinList{" +
                "list=" + list +
                ", position=" + position +
                '}';
    }

}
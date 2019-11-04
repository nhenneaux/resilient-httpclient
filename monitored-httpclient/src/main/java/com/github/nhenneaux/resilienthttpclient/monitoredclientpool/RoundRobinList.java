package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

import java.util.List;
import java.util.Optional;

/**
 * A list that is used for load balancing between service hosts.
 */
interface RoundRobinList<T> {

    /**
     * Returns a {@code boolean} to check if the RoundRobinList contains any services or not.
     *
     * @return true if the list is empty, false if not.
     */
    boolean isEmpty();

    /**
     * Returns a {@code List<T>} of {@code <T>} services.
     *
     * @return the list
     */
    List<T> getList();

    /**
     * Returns an {@code Optional<T>} consisting of the next {@code <T>} service in the RoundRobinList.
     *
     * @return an optional of service
     */
    Optional<T> next();

}

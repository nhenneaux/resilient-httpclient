package com.github.nhenneaux.resilienthttpclient.monitoredclientpool;

/**
 * An item which has an ability to perform a health check on itself
 */
interface ItemWithHealth {

    boolean isHealthy();

}

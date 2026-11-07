package com.rdh.swarm.utils;

@FunctionalInterface
public interface EpicInterface<T, E> {
    E get(T t);
}

package com.boba.gatling.utils;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Util {

  private static final AtomicInteger counter = new AtomicInteger(1);
  public static Iterator<Map<String, Object>> idFeeder = Stream.generate((Supplier<Map<String, Object>>) () ->
          Collections.singletonMap("id", counter.getAndIncrement())).iterator();

}
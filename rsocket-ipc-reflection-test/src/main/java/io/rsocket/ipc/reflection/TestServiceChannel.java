package io.rsocket.ipc.reflection;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TestServiceChannel {

	default Flux<String> channel(Flux<Date> flux) {
		return flux.map(d -> "the date is:" + d);
	}

	default void msg(String msg) {
		System.out.println("msg:" + msg);
	}

	default Stream<String> stream(String msg, Date date) {
		return IntStream.range(0, 10).mapToObj(v -> v).map(v -> v + "- " + msg + " - " + date);
	}

	default int add(int... args) {
		int res = 0;
		for (int arg : args)
			res += arg;
		return res;
	}

	default Flux<Integer> intFlux(int... args) {
		List<Integer> argList = new ArrayList<>();
		for (int val : args)
			argList.add(val);
		return Flux.fromIterable(argList);
	}

	default Mono<Integer> addMono(int... args) {
		int res = 0;
		for (int arg : args)
			res += arg;
		return Mono.just(res);
	}

	default Stream<String> cool(List<Date> vals, String msg) {
		return vals.stream().map(v -> msg + " " + v);
	}
}

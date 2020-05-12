package io.rsocket.ipc.reflection;

import java.util.logging.Level;

import com.google.gson.Gson;

import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.ipc.decoders.MetadataDecoderLFP;
import io.rsocket.ipc.marshallers.GsonMarshaller;
import io.rsocket.ipc.marshallers.GsonUnmarshaller;
import io.rsocket.ipc.reflection.server.RequestHandlingRSocketReflection;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.publisher.Mono;

public class ServerTest {
	private static final Class<?> THIS_CLASS = new Object() {
	}.getClass().getEnclosingClass();
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(THIS_CLASS);

	public static void main(String[] args) throws InterruptedException {
		Class<TestServiceChannel> classType = TestServiceChannel.class;
		TestServiceChannel service = new TestServiceChannel() {
		};
		MetadataDecoderLFP decoder = new MetadataDecoderLFP();
		RequestHandlingRSocketReflection requestHandler;
		{
			requestHandler = new RequestHandlingRSocketReflection(new MetadataDecoderLFP());
			SocketAcceptor socketAcceptor = (setup, client) -> Mono.just(requestHandler);
			RSocketServer.create(socketAcceptor).interceptors(ir -> {
			}).errorConsumer(t -> {
				java.util.logging.Logger.getLogger("[server]").log(Level.SEVERE, "uncaught error", t);
			}).bind(TcpServerTransport.create("localhost", 7000)).block();
		}
		boolean releaseOnParse = true;
		Gson gson = new Gson();
		requestHandler.register(classType, service, new GsonMarshaller(gson), (pt, bb, md) -> {
			return GsonUnmarshaller.apply(gson, pt, true, bb);
		});
		System.out.println("started");
		while (true) {
			Thread.sleep(1000);
		}
	}

}

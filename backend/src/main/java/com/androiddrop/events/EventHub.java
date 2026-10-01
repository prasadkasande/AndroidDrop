package com.androiddrop.events;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import com.androiddrop.device.Device;

/**
 * Tracks open Server-Sent Event streams. A device counts as online while it has at least one stream open (a
 * phone that locks its screen drops the stream and reconnects when it wakes).
 * <p>
 * Each stream has its own outbox: events are queued and written in order by a virtual thread, so a phone on a weak
 * connection never holds up the code that published them (which may be holding the transfer lock).
 */
@Component
public class EventHub {

	/** Events a stream may have waiting before it counts as stuck and is dropped. The page reconnects and resyncs. */
	static final int MAX_BACKLOG = 1000;

	/** One open stream. A device with several tabs open has several. */
	public record Client(String deviceId, boolean host, SseEmitter emitter, ExecutorService outbox) {
	}

	private final Set<Client> clients = ConcurrentHashMap.newKeySet();
	private final ApplicationEventPublisher events;

	public EventHub(ApplicationEventPublisher events) {
		this.events = events;
	}

	public SseEmitter connect(Device device) {
		return connect(device, new SseEmitter(0L)); // never time out; the heartbeat finds dead connections
	}

	SseEmitter connect(Device device, SseEmitter emitter) {
		ExecutorService outbox = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>(MAX_BACKLOG), Thread.ofVirtual().name("sse-" + device.id()).factory());
		Client client = new Client(device.id(), device.host(), emitter, outbox);
		boolean cameOnline = !isOnline(device.id());
		clients.add(client);
		emitter.onCompletion(() -> remove(client));
		emitter.onTimeout(() -> remove(client));
		emitter.onError(ex -> remove(client));
		send(client, () -> event("ready", Map.of("deviceId", device.id())));
		if (cameOnline) {
			events.publishEvent(new DropEvent.DevicesChanged());
		}
		return emitter;
	}

	public void broadcast(String name, Object data, Predicate<Client> audience) {
		clients.stream().filter(audience).forEach(client -> send(client, () -> event(name, data)));
	}

	public void disconnect(String deviceId) {
		clients.stream().filter(client -> client.deviceId().equals(deviceId)).forEach(this::close);
	}

	public Set<String> onlineDeviceIds() {
		return clients.stream().map(Client::deviceId).collect(Collectors.toSet());
	}

	@Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
	void heartbeat() {
		clients.forEach(client -> send(client, () -> SseEmitter.event().comment("ping")));
	}

	/** Close streams up front so graceful shutdown doesn't wait on them. */
	@EventListener(ContextClosedEvent.class)
	void closeAll() {
		clients.forEach(this::close);
	}

	/** Queues an event for one stream. The builder is made per stream because sending consumes it. */
	private void send(Client client, Supplier<SseEventBuilder> event) {
		try {
			client.outbox().execute(() -> {
				try {
					client.emitter().send(event.get());
				}
				catch (IOException | IllegalStateException ex) {
					remove(client);
				}
			});
		}
		catch (RejectedExecutionException ex) {
			// Too far behind to catch up, or already closed.
			close(client);
		}
	}

	private static SseEventBuilder event(String name, Object data) {
		return SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
	}

	private boolean isOnline(String deviceId) {
		return clients.stream().anyMatch(client -> client.deviceId().equals(deviceId));
	}

	private void close(Client client) {
		client.emitter().complete();
		remove(client);
	}

	private void remove(Client client) {
		client.outbox().shutdown();
		if (clients.remove(client) && !isOnline(client.deviceId())) {
			events.publishEvent(new DropEvent.DevicesChanged());
		}
	}

}

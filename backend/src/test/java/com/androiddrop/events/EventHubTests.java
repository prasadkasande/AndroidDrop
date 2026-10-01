package com.androiddrop.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.androiddrop.device.Device;
import com.androiddrop.device.Platform;

class EventHubTests {

	private final EventHub hub = new EventHub(event -> {
	});

	private final CountDownLatch unstick = new CountDownLatch(1);

	@AfterEach
	void release() {
		unstick.countDown();
	}

	@Test
	void aStuckStreamDoesNotHoldUpBroadcastsOrOtherStreams() throws Exception {
		hub.connect(device("stuck"), new StuckEmitter());
		CountingEmitter fast = new CountingEmitter();
		hub.connect(device("fast"), fast);

		long started = System.nanoTime();
		for (int i = 0; i < 20; i++) {
			hub.broadcast("devices", List.of(), client -> true);
		}

		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
		assertThat(fast.awaitCount(21)).as("ready + 20 broadcasts").isTrue();
	}

	@Test
	void aStreamThatFallsTooFarBehindIsDropped() {
		hub.connect(device("stuck"), new StuckEmitter());
		hub.connect(device("fast"), new CountingEmitter());

		for (int i = 0; i < EventHub.MAX_BACKLOG + 10; i++) {
			hub.broadcast("devices", List.of(), client -> true);
		}

		assertThat(hub.onlineDeviceIds()).containsExactly("fast");
	}

	private static Device device(String id) {
		return new Device(id, id, Platform.ANDROID, false, Instant.now(), false, List.of("hash-" + id), null);
	}

	/** Never finishes writing, like a phone that walked out of Wi-Fi range mid-event. */
	private class StuckEmitter extends SseEmitter {

		@Override
		public void send(SseEventBuilder builder) {
			try {
				unstick.await();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

	}

	private static class CountingEmitter extends SseEmitter {

		private final AtomicInteger sent = new AtomicInteger();

		@Override
		public void send(SseEventBuilder builder) {
			sent.incrementAndGet();
		}

		boolean awaitCount(int expected) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (sent.get() < expected && System.nanoTime() < deadline) {
				Thread.sleep(10);
			}
			return sent.get() >= expected;
		}

	}

}
